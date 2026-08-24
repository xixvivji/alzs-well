package com.alzswell.detection.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.security.AuditActor;
import com.alzswell.detection.api.DetectionErrorCode;
import com.alzswell.detection.api.DetectionPolicyRequests.CreatePolicyCommand;
import com.alzswell.detection.api.DetectionPolicyRequests.RuleInput;
import com.alzswell.detection.api.DetectionPolicyRequests.UpdatePolicyCommand;
import com.alzswell.detection.api.DetectionPolicyResponses.AlgorithmVersion;
import com.alzswell.detection.api.DetectionPolicyResponses.AlgorithmVersionList;
import com.alzswell.detection.api.DetectionPolicyResponses.PolicyDetail;
import com.alzswell.detection.api.DetectionPolicyResponses.PolicyList;
import com.alzswell.detection.api.DetectionPolicyResponses.PolicySummary;
import com.alzswell.detection.api.DetectionPolicyResponses.VersionList;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionPolicyService {
    public static final String ALGORITHM_VERSION = "baseline-rules-v2.0.0";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DetectionPolicyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PolicyList policies() {
        List<PolicySummary> items = jdbcTemplate.query("""
                select policy_id, version_code, status, description, rules_hash, row_version,
                       created_at, published_at
                  from detection_policy_version
                 order by created_at desc, policy_id desc
                """, (rs, rowNum) -> summary(rs));
        return new PolicyList(items, items.size());
    }

    @Transactional(readOnly = true)
    public PolicyDetail policy(UUID policyId) {
        List<PolicyDetail> rows = jdbcTemplate.query("""
                select policy_id, version_code, status, description, rules_hash, row_version,
                       created_at, published_at, rules::text, based_on_policy_id
                  from detection_policy_version where policy_id=?
                """, (rs, rowNum) -> new PolicyDetail(summary(rs), rules(rs.getString("rules")),
                        rs.getObject("based_on_policy_id", UUID.class)), policyId);
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.POLICY_NOT_FOUND);
        return rows.getFirst();
    }

    @Transactional
    public PolicyDetail create(CreatePolicyCommand command, AuditActor actor) {
        validateRules(command.rules());
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID id = UUID.randomUUID();
        String json = json(command.rules());
        String hash = sha256(json);
        String versionCode = "detection-policy-draft-" + id.toString().substring(0, 8);
        jdbcTemplate.update("""
                insert into detection_policy_version (
                    policy_id, version_code, status, description, rules, rules_hash,
                    row_version, created_by, created_at
                ) values (?, ?, 'DRAFT', ?, ?::jsonb, ?, 0, ?, ?)
                """, id, versionCode, command.description().trim(), json, hash, actor.legacyActorId(), now);
        event(id, "DRAFT_CREATED", actor, null, "DRAFT", hash, now);
        return policy(id);
    }

    @Transactional
    public PolicyDetail update(UUID policyId, UpdatePolicyCommand command, AuditActor actor) {
        validateRules(command.rules());
        PolicyDetail current = policyForUpdate(policyId);
        if (!current.policy().status().equals("DRAFT")) {
            throw new BusinessException(DetectionErrorCode.POLICY_STATE_CONFLICT);
        }
        String json = json(command.rules());
        String hash = sha256(json);
        int updated = jdbcTemplate.update("""
                update detection_policy_version
                   set description=?, rules=?::jsonb, rules_hash=?, row_version=row_version+1
                 where policy_id=? and status='DRAFT' and row_version=?
                """, command.description().trim(), json, hash, policyId, command.expectedVersion());
        if (updated != 1) throw new BusinessException(DetectionErrorCode.POLICY_VERSION_CONFLICT);
        event(policyId, "DRAFT_UPDATED", actor, "DRAFT", "DRAFT", hash, OffsetDateTime.now(clock));
        return policy(policyId);
    }

    @Transactional
    public PolicyDetail publish(UUID policyId, AuditActor actor) {
        serializeActivation();
        PolicyDetail draft = policyForUpdate(policyId);
        if (!draft.policy().status().equals("DRAFT")) {
            throw new BusinessException(DetectionErrorCode.POLICY_STATE_CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update("update detection_policy_version set status='RETIRED' where status='ACTIVE'");
        String versionCode = nextVersionCode(now);
        jdbcTemplate.update("""
                update detection_policy_version
                   set status='ACTIVE', version_code=?, published_by=?, published_at=?, row_version=row_version+1
                 where policy_id=? and status='DRAFT'
                """, versionCode, actor.legacyActorId(), now, policyId);
        event(policyId, "POLICY_PUBLISHED", actor, "DRAFT", "ACTIVE", draft.policy().rulesHash(), now);
        return policy(policyId);
    }

    @Transactional
    public PolicyDetail rollback(UUID sourcePolicyId, AuditActor actor) {
        serializeActivation();
        PolicyDetail source = policyForUpdate(sourcePolicyId);
        if (!Set.of("ACTIVE", "RETIRED").contains(source.policy().status())) {
            throw new BusinessException(DetectionErrorCode.POLICY_STATE_CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID id = UUID.randomUUID();
        String versionCode = nextVersionCode(now);
        String draftVersionCode = "detection-policy-draft-" + id.toString().substring(0, 8);
        String rulesJson = json(source.rules());
        jdbcTemplate.update("update detection_policy_version set status='RETIRED' where status='ACTIVE'");
        jdbcTemplate.update("""
                insert into detection_policy_version (
                    policy_id, version_code, status, description, rules, rules_hash, based_on_policy_id,
                    row_version, created_by, created_at
                ) values (?, ?, 'DRAFT', ?, ?::jsonb, ?, ?, 0, ?, ?)
                """, id, draftVersionCode, "복귀: " + source.policy().versionCode(), rulesJson,
                source.policy().rulesHash(), sourcePolicyId, actor.legacyActorId(), now);
        jdbcTemplate.update("""
                update detection_policy_version
                   set status='ACTIVE',version_code=?,published_by=?,published_at=?,row_version=row_version+1
                 where policy_id=? and status='DRAFT'
                """, versionCode, actor.legacyActorId(), now, id);
        event(id, "POLICY_ROLLED_BACK", actor, null, "ACTIVE", source.policy().rulesHash(), now);
        return policy(id);
    }

    @Transactional(readOnly = true)
    public VersionList versions() {
        PolicyList list = policies();
        String active = list.items().stream().filter(item -> item.status().equals("ACTIVE"))
                .map(PolicySummary::versionCode).findFirst().orElse(null);
        return new VersionList(list.items(), list.totalCount(), active);
    }

    public AlgorithmVersionList algorithms() {
        return new AlgorithmVersionList(List.of(
                new AlgorithmVersion(ALGORITHM_VERSION, "ACTIVE", false, false)), 1);
    }

    @Transactional(readOnly = true)
    public ActivePolicy activePolicy() {
        List<ActivePolicy> rows = jdbcTemplate.query("""
                select version_code, rules::text, rules_hash
                  from detection_policy_version where status='ACTIVE'
                """, (rs, rowNum) -> new ActivePolicy(rs.getString("version_code"),
                        rules(rs.getString("rules")), rs.getString("rules_hash")));
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.ACTIVE_POLICY_NOT_FOUND);
        return rows.getFirst();
    }

    private PolicyDetail policyForUpdate(UUID id) {
        List<UUID> rows = jdbcTemplate.queryForList(
                "select policy_id from detection_policy_version where policy_id=? for update", UUID.class, id);
        if (rows.size() != 1) throw new BusinessException(DetectionErrorCode.POLICY_NOT_FOUND);
        return policy(id);
    }

    private void serializeActivation() {
        jdbcTemplate.queryForObject(
                "select pg_advisory_xact_lock(hashtext('alzs-well-detection-policy-activation'))",
                Object.class);
    }

    private String nextVersionCode(OffsetDateTime now) {
        return "detection-policy-v" + now.toInstant().toEpochMilli() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private void validateRules(List<RuleInput> rules) {
        Set<String> features = new HashSet<>();
        for (RuleInput rule : rules) {
            if (!features.add(rule.featureCode()) || rule.highDelta().compareTo(rule.triggerDelta()) < 0) {
                throw new BusinessException(DetectionErrorCode.POLICY_RULE_INVALID);
            }
        }
    }

    private void event(UUID id, String type, AuditActor actor, String from, String to,
                       String hash, OffsetDateTime at) {
        jdbcTemplate.update("""
                insert into detection_policy_event (
                    event_id, policy_id, event_type, actor_subject, from_status, to_status, rules_hash, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, type, actor.legacyActorId(), from, to, hash, at);
    }

    private PolicySummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PolicySummary(rs.getObject("policy_id", UUID.class), rs.getString("version_code"),
                rs.getString("status"), rs.getString("description"), rs.getString("rules_hash"),
                rs.getLong("row_version"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("published_at", OffsetDateTime.class));
    }

    private List<RuleInput> rules(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("저장된 정책을 읽을 수 없습니다.", exception); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("정책을 직렬화할 수 없습니다.", exception); }
    }

    private String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record ActivePolicy(String versionCode, List<RuleInput> rules, String rulesHash) {}
}
