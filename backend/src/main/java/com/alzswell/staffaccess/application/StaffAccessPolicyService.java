package com.alzswell.staffaccess.application;

import static com.alzswell.staffaccess.api.StaffAccessErrorCode.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.staffaccess.api.StaffAccessRequests.EvaluationCommand;
import com.alzswell.staffaccess.api.StaffAccessRequests.GrantCommand;
import com.alzswell.staffaccess.api.StaffAccessRequests.RevokeCommand;
import com.alzswell.staffaccess.api.StaffAccessResponses.Evaluation;
import com.alzswell.staffaccess.api.StaffAccessResponses.Grant;
import com.alzswell.staffaccess.api.StaffAccessResponses.GrantEvent;
import com.alzswell.staffaccess.api.StaffAccessResponses.GrantHistory;
import com.alzswell.staffaccess.api.StaffAccessResponses.GrantList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccessPolicyService {
    public static final Set<String> ALLOWED_SCOPES = Set.of(
            "CONSENT_READ", "CONSENT_WRITE", "TRUSTED_CONTACT_READ", "TRUSTED_CONTACT_WRITE",
            "FINANCIAL_INTENT_READ", "CASE_READ", "CASE_ASSIGN", "CASE_REVIEW", "CASE_GUIDANCE",
            "CASE_NOTE", "CASE_FOLLOW_UP", "PRIVACY_REQUEST_WRITE");

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StaffAccessPolicyService(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public GrantList list(String customerId) {
        List<Grant> items = jdbc.sql("select * from staff_access_grant where customer_id=? order by granted_at,grant_id")
                .param(customerId).query(this::map).list();
        return new GrantList(customerId, items, items.size());
    }

    @Transactional
    public Grant detail(String customerId, UUID grantId) { return find(customerId, grantId); }

    @Transactional
    public Grant create(String customerId, GrantCommand command, String idempotencyKey, AuditActor actor) {
        requireAuthenticatedStaff(actor);
        List<String> scopes = normalizeScopes(command.scopes());
        String keyHash = hash(idempotencyKey);
        String requestHash = hash(command.staffPrincipalId() + "|" + command.purposeCode() + "|"
                + String.join(",", scopes) + "|" + command.expiresAt());
        Optional<Grant> replay = findByIdempotency(customerId, keyHash);
        if (replay.isPresent()) return verifyReplay(replay.get(), requestHash);
        requireProtectionStaff(command.staffPrincipalId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!command.expiresAt().isAfter(now) || command.expiresAt().isAfter(now.plusDays(90))) {
            throw new BusinessException(STATE_CONFLICT);
        }
        UUID grantId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                insert into staff_access_grant(
                    grant_id,staff_principal_id,customer_id,purpose_code,scopes,status,granted_by,
                    granted_at,expires_at,idempotency_key_hash,request_hash,row_version
                ) values(?,?,?,?,?::varchar[],'ACTIVE',?,?,?,?,?,1)
                on conflict do nothing
                """).params(grantId, command.staffPrincipalId(), customerId, command.purposeCode(), array(scopes),
                actor.principalId(), now, command.expiresAt(), keyHash, requestHash).update();
        if (inserted == 0) {
            return findByIdempotency(customerId, keyHash)
                    .map(existing -> verifyReplay(existing, requestHash))
                    .orElseThrow(() -> new BusinessException(STATE_CONFLICT));
        }
        event(grantId, "GRANTED", actor, "ACTIVE", scopes, "{}", now);
        return find(customerId, grantId);
    }

    @Transactional
    public Grant revoke(String customerId, UUID grantId, RevokeCommand command, AuditActor actor) {
        requireAuthenticatedStaff(actor);
        Grant before = find(customerId, grantId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed = jdbc.sql("""
                update staff_access_grant set status='REVOKED',revoked_at=?,revocation_reason=?,
                    row_version=row_version+1 where grant_id=? and customer_id=? and status='ACTIVE' and row_version=?
                """).params(now, command.reason().trim(), grantId, customerId, command.expectedVersion()).update();
        if (changed != 1) throw new BusinessException(STATE_CONFLICT);
        event(grantId, "REVOKED", actor, "REVOKED", before.scopes(),
                json(java.util.Map.of("reason", command.reason().trim())), now);
        return find(customerId, grantId);
    }

    @Transactional
    public Evaluation evaluate(EvaluationCommand command, AuditActor actor) {
        requireAuthenticatedStaff(actor);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<Grant> grant = activeGrant(command.staffPrincipalId(), command.customerId(), command.scope(), now);
        UUID evaluationId = UUID.randomUUID();
        if (grant.isPresent()) {
            event(grant.get().grantId(), "EVALUATED", actor, grant.get().status(), grant.get().scopes(),
                    json(java.util.Map.of("evaluationId", evaluationId, "scope", command.scope(), "allowed", true)), now);
        }
        return new Evaluation(evaluationId, command.staffPrincipalId(), command.customerId(), command.scope(),
                grant.isPresent(), grant.map(Grant::grantId).orElse(null),
                grant.isPresent() ? "ALLOW_ACTIVE_GRANT" : "DENY_NO_ACTIVE_GRANT", now, false);
    }

    @Transactional
    public GrantHistory history(String customerId, UUID grantId) {
        find(customerId, grantId);
        List<GrantEvent> items = jdbc.sql("""
                select event_id,event_type,status_snapshot,scopes_snapshot,actor_principal_id,actor_type,
                       detail,occurred_at from staff_access_grant_event where grant_id=? order by occurred_at,event_id
                """).param(grantId).query((rs, row) -> new GrantEvent(rs.getObject("event_id", UUID.class),
                rs.getString("event_type"), rs.getString("status_snapshot"), array(rs.getArray("scopes_snapshot")),
                rs.getObject("actor_principal_id", UUID.class), rs.getString("actor_type"),
                jsonNode(rs.getString("detail")), rs.getObject("occurred_at", OffsetDateTime.class))).list();
        return new GrantHistory(grantId, items, items.size());
    }

    @Transactional
    public UUID require(AuditActor actor, String customerId, String scope, String resourceType, String resourceId) {
        if (!"STAFF".equals(actor.actorType())) return null;
        requireAuthenticatedStaff(actor);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Grant grant = activeGrant(actor.principalId(), customerId, scope, now)
                .orElseThrow(() -> new BusinessException(ACCESS_DENIED));
        event(grant.grantId(), "ACCESS_USED", actor, grant.status(), grant.scopes(),
                json(java.util.Map.of("scope", scope, "resourceType", resourceType,
                        "resourceId", resourceId == null ? "LIST" : resourceId)), now);
        return grant.grantId();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveGrant(UUID principalId, String customerId, String scope) {
        return activeGrant(principalId, customerId, scope, OffsetDateTime.now(clock)).isPresent();
    }

    private Optional<Grant> activeGrant(UUID principalId, String customerId, String scope, OffsetDateTime now) {
        if (principalId == null || !ALLOWED_SCOPES.contains(scope)) return Optional.empty();
        return jdbc.sql("""
                select * from staff_access_grant where staff_principal_id=? and customer_id=?
                  and status='ACTIVE' and expires_at>? and ?=any(scopes)
                order by expires_at desc,grant_id limit 1
                """).params(principalId, customerId, now, scope).query(this::map).optional();
    }

    private Grant find(String customerId, UUID grantId) {
        return jdbc.sql("select * from staff_access_grant where customer_id=? and grant_id=?")
                .params(customerId, grantId).query(this::map).optional()
                .orElseThrow(() -> new BusinessException(GRANT_NOT_FOUND));
    }

    private Optional<Grant> findByIdempotency(String customerId, String keyHash) {
        return jdbc.sql("select * from staff_access_grant where customer_id=? and idempotency_key_hash=?")
                .params(customerId, keyHash).query(this::map).optional();
    }

    private Grant verifyReplay(Grant replay, String requestHash) {
        String stored = jdbc.sql("select request_hash from staff_access_grant where grant_id=?")
                .param(replay.grantId()).query(String.class).single();
        if (!secureEquals(stored, requestHash)) throw new BusinessException(IDEMPOTENCY_CONFLICT);
        return replay;
    }

    private Grant map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Grant(rs.getObject("grant_id", UUID.class), rs.getObject("staff_principal_id", UUID.class),
                rs.getString("customer_id"), rs.getString("purpose_code"), array(rs.getArray("scopes")),
                rs.getString("status"), rs.getObject("granted_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("revoked_at", OffsetDateTime.class),
                rs.getString("revocation_reason"), rs.getLong("row_version"), false, false);
    }

    private void requireAuthenticatedStaff(AuditActor actor) {
        if (!"STAFF".equals(actor.actorType()) || actor.principalId() == null) throw new BusinessException(ACCESS_DENIED);
    }

    private void requireProtectionStaff(UUID principalId) {
        Boolean eligible = jdbc.sql("""
                select exists(select 1 from auth_principal p join auth_principal_role r using(principal_id)
                  where p.principal_id=? and p.status='ACTIVE' and r.role_code='PROTECTION_STAFF')
                """).param(principalId).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(eligible)) throw new BusinessException(PRINCIPAL_NOT_ELIGIBLE);
    }

    private List<String> normalizeScopes(List<String> values) {
        if (values == null || values.isEmpty() || !ALLOWED_SCOPES.containsAll(values)) {
            throw new BusinessException(STATE_CONFLICT);
        }
        return values.stream().distinct().sorted().toList();
    }

    private void event(UUID grantId, String type, AuditActor actor, String status, List<String> scopes,
            String detail, OffsetDateTime occurredAt) {
        jdbc.sql("""
                insert into staff_access_grant_event(event_id,grant_id,event_type,status_snapshot,scopes_snapshot,
                    actor_principal_id,actor_customer_id,actor_session_id,actor_type,detail,occurred_at)
                values(?,?,?,?,?::varchar[],?,?,?,?,?::jsonb,?)
                """).params(UUID.randomUUID(), grantId, type, status, array(scopes), actor.principalId(),
                actor.customerId(), actor.sessionId(), actor.actorType(), detail, occurredAt).update();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private JsonNode jsonNode(String value) {
        try { return objectMapper.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private List<String> array(Array value) throws java.sql.SQLException {
        return value == null ? List.of() : List.of((String[]) value.getArray());
    }

    private String array(List<String> values) { return "{" + String.join(",", values) + "}"; }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
