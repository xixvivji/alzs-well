package com.alzswell.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class SyntheticDatasetIntegrationTest {
    private static final String CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new com.alzswell.test.PgVectorPostgreSqlContainer();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "detection-admin", authorities = {
            "SYNTHETIC_DATASET_ADMIN", "DETECTION_RUN_CREATE", "DETECTION_RUN_READ",
            "DETECTION_PROMOTE", "DETECTION_PROMOTION_READ"
    })
    void registersValidatesIngestsAndDetectsFromSyntheticObservations() throws Exception {
        UUID datasetId = createDataset(validDatasetJson());

        mockMvc.perform(get("/api/v1/admin/synthetic-datasets/{datasetId}", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataset.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.syntheticData").value(true));

        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/validate", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALIDATED"))
                .andExpect(jsonPath("$.data.errors.length()").value(0));

        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/ingest", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INGESTED"))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false));

        MvcResult runResult = mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .header("Idempotency-Key", "synthetic-run-test-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + datasetId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.signalCount").value(3))
                .andExpect(jsonPath("$.data.policyVersion").value("detection-policy-v1.0.0"))
                .andExpect(jsonPath("$.data.policySnapshotHash").isNotEmpty())
                .andExpect(jsonPath("$.data.signals[0].reasonCode").isNotEmpty())
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false))
                .andExpect(jsonPath("$.data.advisoryAiUsed").value(false))
                .andExpect(jsonPath("$.data.externalExecutionCreated").value(false))
                .andReturn();

        JsonNode response = objectMapper.readTree(runResult.getResponse().getContentAsByteArray());
        UUID runId = UUID.fromString(response.path("data").path("detectionRunId").asText());

        mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .header("Idempotency-Key", "synthetic-run-test-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + datasetId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.detectionRunId").value(runId.toString()))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));

        mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .header("Idempotency-Key", "synthetic-run-test-0001")
                        .contentType(APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DETECTION_IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/v1/detection-runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DETECTION_RUN_RETRIEVED"))
                .andExpect(jsonPath("$.data.inputPayloadHash").isNotEmpty())
                .andExpect(jsonPath("$.data.resultHash").isNotEmpty());

        mockMvc.perform(get("/api/v1/detection-runs/{runId}/promotion", runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DETECTION_PROMOTION_NOT_FOUND"));

        MvcResult promotionResult = mockMvc.perform(
                        post("/api/v1/detection-runs/{runId}/promotion", runId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DETECTION_RUN_PROMOTED"))
                .andExpect(jsonPath("$.data.promotedSignalCount").value(3))
                .andExpect(jsonPath("$.data.promotedAlertCount").value(3))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(false))
                .andExpect(jsonPath("$.data.financialActionExecuted").value(false))
                .andExpect(jsonPath("$.data.externalNotificationSent").value(false))
                .andReturn();
        String promotionId = objectMapper.readTree(promotionResult.getResponse().getContentAsByteArray())
                .path("data").path("promotionId").asText();

        mockMvc.perform(post("/api/v1/detection-runs/{runId}/promotion", runId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.promotionId").value(promotionId))
                .andExpect(jsonPath("$.data.idempotencyReplayed").value(true));
        mockMvc.perform(get("/api/v1/detection-runs/{runId}/promotion", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promotionResultHash").isNotEmpty());

        Integer runCount = jdbcTemplate.queryForObject(
                "select count(*) from synthetic_detection_run where customer_id = ?", Integer.class, CUSTOMER_ID);
        assertThat(runCount).isEqualTo(1);
        Integer promotedSignals = jdbcTemplate.queryForObject(
                "select count(*) from customer_detection_signal where source_detection_run_id = ?",
                Integer.class, runId);
        Integer promotedAlerts = jdbcTemplate.queryForObject("""
                select count(*) from operational_alert a
                  join customer_detection_signal s on s.signal_id = a.signal_id
                 where s.source_detection_run_id = ?
                """, Integer.class, runId);
        assertThat(promotedSignals).isEqualTo(3);
        assertThat(promotedAlerts).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "detection-admin", authorities = "SYNTHETIC_DATASET_ADMIN")
    void rejectsIngestionWhenSemanticValidationFails() throws Exception {
        UUID datasetId = createDataset(invalidDatasetJson());

        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/validate", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVALID"))
                .andExpect(jsonPath("$.data.errors.length()").value(1));

        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/ingest", datasetId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNTHETIC_DATASET_STATE_CONFLICT"));
    }

    @Test
    @WithMockUser(username = "detection-admin", authorities = "SYNTHETIC_DATASET_ADMIN")
    void rejectsSensitiveEvidenceBeforeItCanEnterTheImmutableDataset() throws Exception {
        String sensitive = validDatasetJson().replace("예정 거래 누락", "계좌번호 123456789");
        mockMvc.perform(post("/api/v1/admin/synthetic-datasets")
                        .contentType(APPLICATION_JSON).content(sensitive))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        Integer stored = jdbcTemplate.queryForObject("""
                select count(*) from synthetic_detection_dataset
                 where payload::text like '%123456789%'
                """, Integer.class);
        assertThat(stored).isZero();
    }

    @Test
    @WithMockUser(username = "detection-admin", authorities = {
            "SYNTHETIC_DATASET_ADMIN", "DETECTION_RUN_CREATE", "DETECTION_PROMOTE"
    })
    void rejectsPromotionWhenPersistedRunHashesDoNotMatchCanonicalSourcesAndAuditsBothReasons()
            throws Exception {
        UUID sourceRunId = createCompletedRun("integrity-source-" + UUID.randomUUID());
        UUID forgedResultRunId = cloneRun(sourceRunId, false, true);
        UUID forgedInputRunId = cloneRun(sourceRunId, true, false);

        mockMvc.perform(post("/api/v1/detection-runs/{runId}/promotion", forgedResultRunId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DETECTION_PROMOTION_SOURCE_INVALID"));
        mockMvc.perform(post("/api/v1/detection-runs/{runId}/promotion", forgedInputRunId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DETECTION_PROMOTION_SOURCE_INVALID"));

        assertThat(jdbcTemplate.queryForList("""
                select reason_code from detection_promotion_integrity_event
                 where detection_run_id in (?, ?) order by reason_code
                """, String.class, forgedResultRunId, forgedInputRunId))
                .containsExactly("INPUT_HASH_MISMATCH", "RESULT_HASH_MISMATCH");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from detection_run_promotion
                 where detection_run_id in (?, ?)
                """, Integer.class, forgedResultRunId, forgedInputRunId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from customer_detection_signal
                 where source_detection_run_id in (?, ?)
                """, Integer.class, forgedResultRunId, forgedInputRunId)).isZero();
    }

    @Test
    @WithMockUser(username = "ordinary-user", authorities = "DETECTION_READ")
    void blocksNonAdminDatasetAccess() throws Exception {
        mockMvc.perform(post("/api/v1/admin/synthetic-datasets")
                        .contentType(APPLICATION_JSON)
                        .content(validDatasetJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/detection-runs/{runId}/promotion", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/synthetic-datasets/{datasetId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private UUID createDataset(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/synthetic-datasets")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SYNTHETIC_DATASET_CREATED"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data").path("dataset").path("datasetId").asText());
    }

    private UUID createCompletedRun(String idempotencyKey) throws Exception {
        UUID datasetId = createDataset(validDatasetJson());
        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/validate", datasetId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/synthetic-datasets/{datasetId}/ingest", datasetId))
                .andExpect(status().isOk());
        MvcResult run = mockMvc.perform(post("/api/v1/customers/{customerId}/detection-runs", CUSTOMER_ID)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + datasetId + "\"}"))
                .andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(objectMapper.readTree(run.getResponse().getContentAsByteArray())
                .path("data").path("detectionRunId").asText());
    }

    private UUID cloneRun(UUID sourceRunId, boolean forgeInputHash, boolean forgeResultHash) {
        UUID clonedRunId = UUID.randomUUID();
        String uniqueHash = "sha256:" + UUID.randomUUID().toString().replace("-", "").repeat(2);
        String forgedHash = "sha256:" + "f".repeat(64);
        jdbcTemplate.update("""
                insert into synthetic_detection_run(
                    detection_run_id,dataset_id,customer_id,status,algorithm_version,
                    policy_version,policy_snapshot_hash,idempotency_key_hash,request_hash,
                    input_payload_hash,result_payload,result_hash,signal_count,started_at,completed_at
                )
                select ?,dataset_id,customer_id,status,algorithm_version,
                       policy_version,policy_snapshot_hash,?,request_hash,
                       case when ? then ? else input_payload_hash end,
                       result_payload,
                       case when ? then ? else result_hash end,
                       signal_count,started_at,completed_at
                  from synthetic_detection_run where detection_run_id=?
                """, clonedRunId, uniqueHash, forgeInputHash, forgedHash,
                forgeResultHash, forgedHash, sourceRunId);
        return clonedRunId;
    }

    private String validDatasetJson() {
        return """
                {
                  "datasetName":"탐지 통합 테스트",
                  "customerId":"SYN_CUSTOMER_FIN_MGMT_001",
                  "observations":[
                    {"featureCode":"MISSED_RECURRING_PAYMENT","baselineValue":0,"currentValue":1,"unit":"COUNT",
                     "evidence":[{"evidenceType":"TRANSACTION","sourceReference":"TEST-MISSED-001",
                     "occurredAt":"2026-08-10T00:00:00Z","description":"예정 거래 누락"}]},
                    {"featureCode":"DUPLICATE_TRANSFER","baselineValue":0,"currentValue":2,"unit":"COUNT",
                     "evidence":[{"evidenceType":"TRANSACTION","sourceReference":"TEST-DUP-001",
                     "occurredAt":"2026-08-12T01:00:00Z","amount":500000,"currency":"KRW","description":"첫 송금"},
                     {"evidenceType":"TRANSACTION","sourceReference":"TEST-DUP-002",
                     "occurredAt":"2026-08-12T01:02:00Z","amount":500000,"currency":"KRW","description":"두 번째 송금"}]},
                    {"featureCode":"REPEATED_CONFIRMATION","baselineValue":1,"currentValue":5,"unit":"COUNT",
                     "evidence":[{"evidenceType":"INTERACTION","sourceReference":"TEST-CONFIRM-001",
                     "occurredAt":"2026-08-13T03:00:00Z","description":"반복 확인"}]}
                  ]
                }
                """;
    }

    private String invalidDatasetJson() {
        return """
                {
                  "datasetName":"잘못된 합성 데이터",
                  "customerId":"SYN_CUSTOMER_FIN_MGMT_001",
                  "observations":[
                    {"featureCode":"DUPLICATE_TRANSFER","baselineValue":0,"currentValue":1,"unit":"COUNT",
                     "evidence":[{"evidenceType":"TRANSACTION","sourceReference":"TEST-INVALID-001",
                     "occurredAt":"2026-08-12T01:00:00Z","description":"중복 기준 미달"}]}
                  ]
                }
                """;
    }
}
