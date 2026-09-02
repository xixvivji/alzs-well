package com.alzswell.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@WithMockUser(username = "policy-admin", authorities = {"DETECTION_POLICY_READ", "DETECTION_POLICY_WRITE"})
class DetectionPolicyIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void reportsAiQualityWithoutExposingQueryTextOrExternalActions() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai-quality/summary").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AI_QUALITY_SUMMARY_RETRIEVED"))
                .andExpect(jsonPath("$.data.windowHours").value(24))
                .andExpect(jsonPath("$.data.status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.syntheticDataOnly").value(true))
                .andExpect(jsonPath("$.data.externalActionsExecuted").value(false));
        mockMvc.perform(get("/api/v1/admin/ai-quality/summary").param("hours", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsUpdatesPublishesAndRollsBackVersionedPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/admin/policies/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeVersion").value("detection-policy-v1.0.0"));
        mockMvc.perform(get("/api/v1/admin/algorithms/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].advisoryAiUsed").value(false))
                .andExpect(jsonPath("$.data.items[0].externalProviderCalled").value(false));

        UUID draftId = createDraft();
        mockMvc.perform(put("/api/v1/admin/rules/{ruleId}", draftId)
                        .contentType(APPLICATION_JSON).content(updateJson(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policy.version").value(1));
        mockMvc.perform(put("/api/v1/admin/rules/{ruleId}", draftId)
                        .contentType(APPLICATION_JSON).content(updateJson(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DETECTION_POLICY_VERSION_CONFLICT"));

        MvcResult published = mockMvc.perform(post("/api/v1/admin/rules/{ruleId}/publish", draftId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policy.status").value("ACTIVE"))
                .andReturn();
        String activeVersion = objectMapper.readTree(published.getResponse().getContentAsByteArray())
                .at("/data/policy/versionCode").asText();

        MvcResult rollback = mockMvc.perform(post("/api/v1/admin/rules/{ruleId}/rollback", draftId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.policy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.basedOnPolicyId").value(draftId.toString()))
                .andReturn();
        String rolledBackVersion = objectMapper.readTree(rollback.getResponse().getContentAsByteArray())
                .at("/data/policy/versionCode").asText();
        assertThat(rolledBackVersion).isNotEqualTo(activeVersion);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from detection_policy_version where status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from detection_policy_event where actor_subject='policy-admin'", Integer.class))
                .isEqualTo(4);
        UUID eventId = jdbcTemplate.queryForObject(
                "select event_id from detection_policy_event where actor_subject='policy-admin' limit 1", UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update detection_policy_event set event_type=event_type where event_id=?", eventId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from detection_policy_event where event_id=?", eventId))
                .hasMessageContaining("append-only");
    }

    @Test
    void rejectsDuplicateFeaturesAndBlocksNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rules")
                        .contentType(APPLICATION_JSON).content("""
                        {"description":"잘못된 중복 정책","rules":[
                          {"featureCode":"DUPLICATE_TRANSFER","triggerDelta":0,"highDelta":1,"reasonCode":"DUPLICATE_TRANSFER"},
                          {"featureCode":"DUPLICATE_TRANSFER","triggerDelta":0,"highDelta":2,"reasonCode":"DUPLICATE_TRANSFER"}]}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DETECTION_POLICY_RULE_INVALID"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from detection_policy_version where status='DRAFT'", Integer.class)).isZero();
    }

    @Test
    void databaseRejectsDirectActiveInsertAndPublishedPolicyRewrite() {
        UUID activeId = jdbcTemplate.queryForObject(
                "select policy_id from detection_policy_version where status='ACTIVE'", UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update detection_policy_version set description='직접 변조' where policy_id=?
                """, activeId)).isInstanceOf(org.springframework.dao.DataAccessException.class)
                .satisfies(exception -> assertThat(((org.springframework.dao.DataAccessException) exception)
                        .getMostSpecificCause().getMessage()).contains("invalid or unaudited"));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into detection_policy_version(policy_id,version_code,status,description,rules,rules_hash,
                    row_version,created_by,created_at,published_by,published_at)
                values(?,'direct-active-test','ACTIVE','직접 활성','[]'::jsonb,?,0,'test',now(),'test',now())
                """, UUID.randomUUID(), "sha256:" + "a".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .satisfies(exception -> assertThat(((org.springframework.dao.DataAccessException) exception)
                        .getMostSpecificCause().getMessage()).contains("must be inserted as draft"));
    }

    @Test
    @WithMockUser(username = "customer", authorities = "DETECTION_READ")
    void requiresPolicyAdministratorPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rules")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/ai-quality/summary")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/rules").contentType(APPLICATION_JSON).content(createJson()))
                .andExpect(status().isForbidden());
    }

    private UUID createDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/rules")
                        .contentType(APPLICATION_JSON).content(createJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.policy.status").value("DRAFT"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/policy/ruleId").asText());
    }

    private String createJson() {
        return """
                {"description":"정책 관리 통합 테스트","rules":[
                  {"featureCode":"MISSED_RECURRING_PAYMENT","triggerDelta":0,"highDelta":1,"reasonCode":"MISSED_RECURRING_PAYMENT"},
                  {"featureCode":"DUPLICATE_TRANSFER","triggerDelta":0,"highDelta":1,"reasonCode":"DUPLICATE_TRANSFER"},
                  {"featureCode":"REPEATED_CONFIRMATION","triggerDelta":1,"highDelta":5,"reasonCode":"REPEATED_CONFIRMATION"}]}
                """;
    }

    private String updateJson(long version) {
        return """
                {"description":"변경된 정책 관리 통합 테스트","rules":[
                  {"featureCode":"MISSED_RECURRING_PAYMENT","triggerDelta":0,"highDelta":1,"reasonCode":"MISSED_RECURRING_PAYMENT"},
                  {"featureCode":"DUPLICATE_TRANSFER","triggerDelta":0,"highDelta":1,"reasonCode":"DUPLICATE_TRANSFER"},
                  {"featureCode":"REPEATED_CONFIRMATION","triggerDelta":1,"highDelta":5,"reasonCode":"REPEATED_CONFIRMATION"}],
                 "expectedVersion":__VERSION__}
                """.replace("__VERSION__", Long.toString(version));
    }
}
