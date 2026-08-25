package com.alzswell.inbox;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class OperationalInboxIntegrationTest {
    private static final String CUSTOMER="SYN_CUSTOMER_FIN_MGMT_001";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES=new com.alzswell.test.PgVectorPostgreSqlContainer();
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

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

    @Test @WithMockUser(username=CUSTOMER,authorities="INBOX_READ")
    void cursorPreservesPostgresMicrosecondsBetweenPages() throws Exception {
        UUID first=UUID.fromString("95000000-0000-0000-0000-000000000001");
        UUID second=UUID.fromString("95000000-0000-0000-0000-000000000002");
        jdbc.update("insert into customer_inbox_message(message_id,customer_id,message_type,title,body,message_version,created_at) values(?,?, 'SERVICE_NOTICE','첫 알림','첫 본문',1,?) on conflict do nothing",
                first,CUSTOMER,OffsetDateTime.parse("2099-01-01T00:00:00.123100Z"));
        jdbc.update("insert into customer_inbox_message(message_id,customer_id,message_type,title,body,message_version,created_at) values(?,?, 'SERVICE_NOTICE','둘째 알림','둘째 본문',1,?) on conflict do nothing",
                second,CUSTOMER,OffsetDateTime.parse("2099-01-01T00:00:00.123900Z"));
        MvcResult page=mockMvc.perform(get("/api/v1/customers/{customerId}/inbox",CUSTOMER).param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].messageId").value(second.toString()))
                .andReturn();
        String cursor=objectMapper.readTree(page.getResponse().getContentAsByteArray()).at("/data/nextCursor").asText();
        mockMvc.perform(get("/api/v1/customers/{customerId}/inbox",CUSTOMER).param("limit","1").param("cursor",cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].messageId").value(first.toString()));
    }
}
