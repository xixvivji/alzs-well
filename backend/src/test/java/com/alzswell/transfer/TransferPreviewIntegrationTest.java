package com.alzswell.transfer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TransferPreviewIntegrationTest {
    private static final String CUSTOMER = "SYN_CUSTOMER_FIN_MGMT_001";
    private static final String ACCOUNT = "95000000-0000-0000-0000-000000000001";
    private static final String BENEFICIARY = "95800000-0000-0000-0000-000000000001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsMaskedBeneficiariesAndSyntheticLimit() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/beneficiaries", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].displayName").value("합성수취인 *01"))
                .andExpect(jsonPath("$.data.items[0].maskedAccountReference").value("안심은행 110-***-**11"))
                .andExpect(jsonPath("$.data.externalProviderCalled").value(false));

        mockMvc.perform(get("/api/v1/customers/{customerId}/transfer-limits", CUSTOMER).with(readUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perTransferLimit").value(5000000))
                .andExpect(jsonPath("$.data.dailyRemainingAmount").value(8800000))
                .andExpect(jsonPath("$.data.syntheticData").value(true));
    }

    @Test
    void simulationAndValidationNeverCreateATransfer() throws Exception {
        mockMvc.perform(post("/api/v1/transfer-simulations").with(evaluateUser())
                        .contentType(APPLICATION_JSON).content(simulationBody("1000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcomeCode").value("SIMULATION_ALLOWED"))
                .andExpect(jsonPath("$.data.projectedAvailableBalance").value(17000000))
                .andExpect(jsonPath("$.data.transferCreated").value(false))
                .andExpect(jsonPath("$.data.authorizationCreated").value(false));

        mockMvc.perform(post("/api/v1/transfer-validations").with(evaluateUser())
                        .contentType(APPLICATION_JSON).content(validationBody("1000000", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.decisionCode").value("PREVIEW_BLOCKED"))
                .andExpect(jsonPath("$.data.checks[6].checkCode").value("RECIPIENT_CONFIRMED"))
                .andExpect(jsonPath("$.data.transferCreated").value(false));
    }

    @Test
    void ownershipAuthorityLimitsAndUnknownResourcesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/beneficiaries", CUSTOMER)
                        .with(user("OTHER_CUSTOMER").authorities(() -> "TRANSFER_PREVIEW_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers/{customerId}/beneficiaries", CUSTOMER).with(user(CUSTOMER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/transfer-simulations")
                        .with(user("OTHER_CUSTOMER").authorities(() -> "TRANSFER_PREVIEW_EVALUATE"))
                        .contentType(APPLICATION_JSON).content(simulationBody("1000000")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/transfer-simulations").with(evaluateUser())
                        .contentType(APPLICATION_JSON).content(simulationBody("6000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcomeCode").value("SIMULATION_BLOCKED"))
                .andExpect(jsonPath("$.data.checks[4].passed").value(false));

        mockMvc.perform(post("/api/v1/transfer-simulations").with(evaluateUser())
                        .contentType(APPLICATION_JSON).content(simulationBody("1000.50")))
                .andExpect(status().isBadRequest());

        String unknown = ("{\"customerId\":\"%s\",\"sourceAccountId\":\"%s\","
                + "\"beneficiaryId\":\"95800000-0000-0000-0000-999999999999\","
                + "\"amount\":1000,\"currency\":\"KRW\"}").formatted(CUSTOMER, ACCOUNT);
        mockMvc.perform(post("/api/v1/transfer-simulations").with(evaluateUser())
                        .contentType(APPLICATION_JSON).content(unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_PREVIEW_RESOURCE_NOT_FOUND"));
    }

    @Test
    void transferSnapshotsAreAppendOnly() {
        assertThatThrownBy(() -> jdbc.update(
                "update customer_beneficiary_snapshot set favorite=false where beneficiary_id=?",
                UUID.fromString(BENEFICIARY)))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from customer_transfer_limit_snapshot where limit_snapshot_id=?",
                UUID.fromString("95900000-0000-0000-0000-000000000001")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("""
                insert into customer_beneficiary_snapshot values(
                    '95800000-0000-0000-0000-999999999999',?,'SYNTHETIC_BANK',
                    '실명수취인*','010-1234-5678*','PERSON','ACTIVE',false,
                    'SYNTHETIC_PROVIDER','2026-08-14',repeat('a',64)
                )
                """, CUSTOMER)).isInstanceOf(DataAccessException.class);
    }

    private String simulationBody(String amount) {
        return "{\"customerId\":\"%s\",\"sourceAccountId\":\"%s\",\"beneficiaryId\":\"%s\","
                .formatted(CUSTOMER, ACCOUNT, BENEFICIARY)
                + "\"amount\":%s,\"currency\":\"KRW\"}".formatted(amount);
    }

    private String validationBody(String amount, boolean confirmed) {
        return "{\"customerId\":\"%s\",\"sourceAccountId\":\"%s\",\"beneficiaryId\":\"%s\","
                .formatted(CUSTOMER, ACCOUNT, BENEFICIARY)
                + "\"amount\":%s,\"currency\":\"KRW\",\"purposeCode\":\"FAMILY_SUPPORT\","
                .formatted(amount)
                + "\"recipientConfirmed\":%s}".formatted(confirmed);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readUser() {
        return user(CUSTOMER).authorities(() -> "TRANSFER_PREVIEW_READ");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor evaluateUser() {
        return user(CUSTOMER).authorities(() -> "TRANSFER_PREVIEW_EVALUATE");
    }
}
