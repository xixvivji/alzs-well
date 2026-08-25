package com.alzswell.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class TransferTemplateIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final String ACCOUNT = "95000000-0000-0000-0000-000000000001";
    private static final String BENEFICIARY = "95800000-0000-0000-0000-000000000001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @Transactional
    void createsListsAndDeletesWithoutExecutingATransfer() throws Exception {
        String body = createBody("생활비 양식", ACCOUNT, BENEFICIARY);
        MvcResult created = mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-001")
                        .contentType(APPLICATION_JSON).content(body).with(writeUser()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TRANSFER_TEMPLATE_CREATED"))
                .andExpect(jsonPath("$.data.maskedSourceAccountNumber").value("110-***-**01"))
                .andExpect(jsonPath("$.data.maskedBeneficiaryAccount").value("안심은행 110-***-**11"))
                .andExpect(jsonPath("$.data.externalActionAvailable").value(false))
                .andExpect(jsonPath("$.data.externalActionExecuted").value(false))
                .andReturn();
        UUID templateId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .path("data").path("templateId").asText());

        mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-001")
                        .contentType(APPLICATION_JSON).content(body).with(writeUser()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.templateId").value(templateId.toString()));
        assertThat(jdbc.queryForObject("select count(*) from customer_transfer_template_event", Integer.class))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/audit/events")
                        .param("sourceType", "TRANSFER_TEMPLATE")
                        .param("customerId", CUSTOMER)
                        .with(user("AUDITOR").authorities(() -> "AUDIT_READ_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceType").value("TRANSFER_TEMPLATE"))
                .andExpect(jsonPath("$.data.items[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(templateId.toString()));

        mockMvc.perform(get("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.maxTemplates").value(20));

        for (int call = 0; call < 2; call++) {
            mockMvc.perform(delete("/api/v1/customers/{customerId}/transfer-templates/{templateId}",
                            CUSTOMER, templateId).header("Idempotency-Key", "template-delete-001")
                            .with(writeUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DELETED"))
                    .andExpect(jsonPath("$.data.alreadyDeleted").value(false))
                    .andExpect(jsonPath("$.data.externalActionExecuted").value(false));
        }
        mockMvc.perform(delete("/api/v1/customers/{customerId}/transfer-templates/{templateId}",
                        CUSTOMER, templateId).header("Idempotency-Key", "template-delete-002")
                        .with(writeUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyDeleted").value(true));
        assertThat(jdbc.queryForObject("select count(*) from customer_transfer_template_event", Integer.class))
                .isEqualTo(2);
        mockMvc.perform(get("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER).with(readUser()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @Transactional
    void enforcesOwnershipAuthorityValidationAndIdempotencyConflict() throws Exception {
        String body = createBody("가족 지원", ACCOUNT, BENEFICIARY);
        mockMvc.perform(get("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "TRANSFER_TEMPLATE_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-101")
                        .contentType(APPLICATION_JSON).content(body).with(writeUser()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-101")
                        .contentType(APPLICATION_JSON)
                        .content(createBody("다른 양식", ACCOUNT, BENEFICIARY)).with(writeUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_TEMPLATE_IDEMPOTENCY_CONFLICT"));
        mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-102")
                        .contentType(APPLICATION_JSON)
                        .content(createBody("계좌번호 12345678", ACCOUNT, BENEFICIARY)).with(writeUser()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-103")
                        .contentType(APPLICATION_JSON)
                        .content(createBody("없는 수취인", ACCOUNT,
                                "95800000-0000-0000-0000-999999999999")).with(writeUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_TEMPLATE_RESOURCE_NOT_FOUND"));
    }

    @Test
    @Transactional
    void protectsCoreFieldsAndAuditHistoryAtDatabaseLevel() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-201")
                        .contentType(APPLICATION_JSON)
                        .content(createBody("보호 양식", ACCOUNT, BENEFICIARY)).with(writeUser()))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsByteArray()).path("data");
        UUID templateId = UUID.fromString(data.path("templateId").asText());

        assertThatThrownBy(() -> jdbc.update(
                "update customer_transfer_template set template_name='변조' where template_id=?", templateId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
    }

    @Test
    @Transactional
    void protectsTransferTemplateAuditHistoryAtDatabaseLevel() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/customers/{customerId}/transfer-templates", CUSTOMER)
                        .header("Idempotency-Key", "template-create-202")
                        .contentType(APPLICATION_JSON)
                        .content(createBody("감사 보호 양식", ACCOUNT, BENEFICIARY)).with(writeUser()))
                .andExpect(status().isCreated()).andReturn();
        UUID templateId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .path("data").path("templateId").asText());

        assertThatThrownBy(() -> jdbc.update(
                "delete from customer_transfer_template_event where template_id=?", templateId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private String createBody(String name, String accountId, String beneficiaryId) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "templateName", name,
                "sourceAccountId", accountId,
                "beneficiaryId", beneficiaryId,
                "amount", 850000,
                "currency", "KRW",
                "purposeCode", "FAMILY_SUPPORT"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "TRANSFER_TEMPLATE_READ");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor writeUser() {
        return user(CUSTOMER).authorities(() -> "TRANSFER_TEMPLATE_WRITE");
    }
}
