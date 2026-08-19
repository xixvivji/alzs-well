package com.alzswell.inbox;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class OperationalInboxIntegrationTest {
    private static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test @WithMockUser(username=CUSTOMER,authorities={"INBOX_READ","INBOX_WRITE"})
    void readsMessageAndUpdatesPreferencesWithoutExternalDelivery() throws Exception {
        UUID messageId=jdbc.queryForObject("select message_id from customer_inbox_message where customer_id=? limit 1",UUID.class,CUSTOMER);
        mockMvc.perform(get("/api/v1/customers/{customerId}/inbox",CUSTOMER).param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].externalDeliveryExecuted").value(false));
        mockMvc.perform(post("/api/v1/customers/{customerId}/inbox/{messageId}/read",CUSTOMER,messageId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.version").value(2));
        mockMvc.perform(put("/api/v1/customers/{customerId}/notification-preferences",CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"changeAlertEnabled\":true,\"followUpEnabled\":false,\"serviceNoticeEnabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.followUpEnabled").value(false))
                .andExpect(jsonPath("$.data.externalDeliveryEnabled").value(false));
    }

    @Test @WithMockUser(username="staff",authorities="NOTIFICATION_PREVIEW")
    void previewsOnlyApprovedInternalTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/notification-previews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"CHANGE_ALERT_RECHECK\",\"facts\":{\"reason\":\"정기납부 누락\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.externalDeliveryExecuted").value(false));
        mockMvc.perform(post("/api/v1/notification-previews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"UNAPPROVED\",\"facts\":{}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTIFICATION_TEMPLATE_NOT_ALLOWED"));
    }

    @Test @WithMockUser(username="OTHER",authorities={"INBOX_READ","INBOX_WRITE"})
    void blocksCrossCustomerAccess() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/inbox",CUSTOMER)).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username=CUSTOMER,authorities="INBOX_READ")
    void rejectsMalformedCursor() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{customerId}/inbox",CUSTOMER).param("cursor","broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INBOX_INVALID_CURSOR"));
    }
}
