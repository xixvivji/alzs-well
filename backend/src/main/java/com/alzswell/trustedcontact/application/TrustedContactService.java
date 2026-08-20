package com.alzswell.trustedcontact.application;

import static com.alzswell.trustedcontact.api.TrustedContactErrorCode.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.trustedcontact.api.TrustedContactRequests.*;
import com.alzswell.trustedcontact.api.TrustedContactResponses.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustedContactService {
    private static final Pattern SAFE_MASK = Pattern.compile("^[0-9+]{2,4}-\\*{3,8}-[0-9]{2,4}$");
    private final JdbcClient jdbc;
    private final Clock clock;

    public TrustedContactService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public ContactList list(String customerId, AuditActor actor) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Contact> items = jdbc.sql("""
                select t.*,
                       (select array_agg(s.scope_code order by s.scope_code)
                          from trusted_contact_scope s where s.contact_id=t.contact_id) scopes
                  from trusted_contact t
                  join customer_consent c on c.consent_id=t.consent_id
                 where t.customer_id=? and t.status='ACTIVE' and t.expires_at>?
                   and c.status='GRANTED' and c.expires_at>?
                 order by t.created_at,t.contact_id
                """).params(customerId, now, now).query(this::map).list();
        auditRead(customerId, null, null, "TRUSTED_CONTACT_LIST_READ", actor,
                hash("trusted-contact-list:" + customerId), now);
        return new ContactList(customerId, items, items.size(), false);
    }

    @Transactional
    public Contact detail(String customerId, UUID id, AuditActor actor) {
        Contact contact = find(customerId, id);
        auditRead(customerId, contact.consentId(), id, "TRUSTED_CONTACT_DETAIL_READ", actor,
                hash("trusted-contact-detail:" + id), OffsetDateTime.now(clock));
        return contact;
    }

    @Transactional
    public Contact create(String customerId, CreateCommand command, String idempotencyKey, AuditActor actor) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String masked = normalizeMasked(command.maskedContact());
        List<String> scopes = normalize(command.scopes());
        String keyHash = hash(idempotencyKey);
        String requestHash = hash(command.consentId() + ":" + command.displayName().trim() + ":"
                + command.relationshipCode() + ":" + masked + ":" + String.join(",", scopes) + ":"
                + command.expiresAt());

        Optional<Replay> existing = findReplay(customerId, keyHash);
        if (existing.isPresent()) return replay(customerId, existing.get(), requestHash);

        lockConsent(customerId, command.consentId(), command.expiresAt(), now);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.sql("""
                insert into trusted_contact(
                    contact_id,customer_id,consent_id,display_name,relationship_code,masked_contact,
                    recipient_accepted,acceptance_status,status,valid_from,expires_at,row_version,
                    created_at,updated_at,idempotency_key_hash,request_hash
                ) values(?,?,?,?,?,?,false,'PENDING_ACCEPTANCE','ACTIVE',?,?,1,?,?,?,?)
                on conflict (customer_id,idempotency_key_hash) where idempotency_key_hash is not null do nothing
                """).params(id, customerId, command.consentId(), command.displayName().trim(),
                command.relationshipCode(), masked, now, command.expiresAt(), now, now, keyHash, requestHash).update();
        if (inserted == 0) return replay(customerId, findReplay(customerId, keyHash).orElseThrow(), requestHash);

        replaceScopes(id, scopes);
        event(id, "CREATED", actor, null, now, 1);
        return find(customerId, id);
    }

    @Transactional
    public Contact update(String customerId, UUID id, UpdateCommand command, AuditActor actor) {
        Contact before = find(customerId, id);
        OffsetDateTime now = OffsetDateTime.now(clock);
        lockConsent(customerId, before.consentId(), command.expiresAt(), now);
        int changed = jdbc.sql("""
                update trusted_contact set expires_at=:expires,row_version=row_version+1,updated_at=:now
                 where contact_id=:id and customer_id=:customer and status='ACTIVE'
                   and row_version=:version and expires_at>:now
                """).param("expires", command.expiresAt()).param("now", now).param("id", id)
                .param("customer", customerId).param("version", command.expectedVersion()).update();
        if (changed == 0) throw new BusinessException(STATE_CONFLICT);
        replaceScopes(id, normalize(command.scopes()));
        event(id, "UPDATED", actor, null, now, before.version() + 1);
        return find(customerId, id);
    }

    @Transactional
    public Contact revoke(String customerId, UUID id, RevokeCommand command, AuditActor actor) {
        Contact before = find(customerId, id);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed = jdbc.sql("""
                update trusted_contact set status='REVOKED',revoked_at=:now,revocation_reason=:reason,
                    row_version=row_version+1,updated_at=:now
                 where contact_id=:id and customer_id=:customer and status='ACTIVE' and row_version=:version
                """).param("now", now).param("reason", command.reason()).param("id", id)
                .param("customer", customerId).param("version", command.expectedVersion()).update();
        if (changed == 0) throw new BusinessException(STATE_CONFLICT);
        event(id, "REVOKED", actor, command.reason(), now, before.version() + 1);
        return find(customerId, id);
    }

    private void lockConsent(String customerId, UUID consentId, OffsetDateTime contactExpiry, OffsetDateTime now) {
        List<Boolean> rows = jdbc.sql("""
                select exists(select 1 from customer_consent_scope s
                               where s.consent_id=c.consent_id and s.scope_code='CONTACT_MINIMUM')
                  from customer_consent c
                 where c.customer_id=? and c.consent_id=? and c.purpose_code='TRUSTED_CONTACT_DISCLOSURE'
                   and c.status='GRANTED' and c.expires_at>=? and c.expires_at>?
                 for update
                """).params(customerId, consentId, contactExpiry, now).query(Boolean.class).list();
        if (rows.size() != 1 || !rows.getFirst()) throw new BusinessException(CONSENT_NOT_ELIGIBLE);
    }

    private Contact find(String customerId, UUID id) {
        return jdbc.sql("""
                select t.*,
                       (select array_agg(s.scope_code order by s.scope_code)
                          from trusted_contact_scope s where s.contact_id=t.contact_id) scopes
                  from trusted_contact t where t.customer_id=? and t.contact_id=?
                """).params(customerId, id).query(this::map).optional()
                .orElseThrow(() -> new BusinessException(CONTACT_NOT_FOUND));
    }

    private Optional<Replay> findReplay(String customerId, String keyHash) {
        return jdbc.sql("select contact_id,request_hash from trusted_contact where customer_id=? and idempotency_key_hash=?")
                .params(customerId, keyHash)
                .query((rs, row) -> new Replay(rs.getObject(1, UUID.class), rs.getString(2))).optional();
    }

    private Contact replay(String customerId, Replay replay, String requestHash) {
        if (!secureEquals(replay.requestHash(), requestHash)) throw new BusinessException(IDEMPOTENCY_CONFLICT);
        return find(customerId, replay.id());
    }

    private Contact map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Contact(rs.getObject("contact_id", UUID.class), rs.getString("customer_id"),
                rs.getObject("consent_id", UUID.class), rs.getString("display_name"),
                rs.getString("relationship_code"), rs.getString("masked_contact"),
                rs.getBoolean("recipient_accepted"), rs.getString("acceptance_status"),
                rs.getString("status"), array(rs.getArray("scopes")),
                rs.getObject("valid_from", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getLong("row_version"), false, false);
    }

    private void replaceScopes(UUID id, List<String> scopes) {
        jdbc.sql("delete from trusted_contact_scope where contact_id=?").param(id).update();
        scopes.forEach(scope -> jdbc.sql("insert into trusted_contact_scope values(?,?)").params(id, scope).update());
    }

    private void event(UUID id, String type, AuditActor actor, String reason, OffsetDateTime at, long version) {
        jdbc.sql("""
                insert into trusted_contact_event(
                    event_id,contact_id,event_type,actor_id,reason,occurred_at,row_version,
                    actor_principal_id,actor_customer_id,actor_session_id,actor_type
                ) values(?,?,?,?,?,?,?,?,?,?,?)
                """).params(UUID.randomUUID(), id, type, actor.legacyActorId(), reason, at, version,
                actor.principalId(), actor.customerId(), actor.sessionId(), actor.actorType()).update();
    }

    private void auditRead(String customerId, UUID consentId, UUID contactId, String eventType,
            AuditActor actor, String requestHash, OffsetDateTime at) {
        jdbc.sql("""
                insert into consent_access_audit_event(
                    evaluation_id,customer_id,consent_id,event_type,actor_principal_id,actor_customer_id,
                    actor_session_id,actor_type,policy_version,request_hash,decision,detail,occurred_at
                ) values(?,?,?,?,?,?,?,?,null,?,'READ',jsonb_build_object('contactId',cast(? as uuid)),?)
                """).params(UUID.randomUUID(), customerId, consentId, eventType, actor.principalId(),
                actor.customerId(), actor.sessionId(), actor.actorType(), requestHash, contactId, at).update();
    }

    private String normalizeMasked(String value) {
        if (value == null) throw new BusinessException(INVALID_MASKED_CONTACT);
        String normalized = value.trim();
        if (!SAFE_MASK.matcher(normalized).matches()) throw new BusinessException(INVALID_MASKED_CONTACT);
        return normalized;
    }

    private List<String> normalize(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()
                || scopes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(INVALID_SCOPE);
        }
        return scopes.stream().distinct().sorted().toList();
    }

    private List<String> array(Array value) throws java.sql.SQLException {
        return value == null ? List.of() : List.of((String[]) value.getArray());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private record Replay(UUID id, String requestHash) {}
}
