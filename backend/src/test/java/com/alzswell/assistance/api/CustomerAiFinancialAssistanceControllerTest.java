package com.alzswell.assistance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.demo.api.AiFinancialAssistanceResponses.ChangeAnalysis;
import com.alzswell.demo.application.DemoAiFinancialAssistanceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerAiFinancialAssistanceControllerTest {
    @Mock DemoAiFinancialAssistanceService assistanceService;

    @Test
    void delegatesMemberScopedChangeAnalysisWithoutAcceptingClientFeatureValues() {
        ChangeAnalysis expected = new ChangeAnalysis(
                60, 30, 90, List.of(), "FASTAPI_EWMA_CUSUM", false,
                List.of(), true, false, false);
        when(assistanceService.analyzeMember("SYN_CUSTOMER_001")).thenReturn(expected);

        ApiResponse<ChangeAnalysis> body = new CustomerAiFinancialAssistanceController(assistanceService)
                .analyze("SYN_CUSTOMER_001").getBody();

        assertThat(body).isNotNull();
        assertThat(body.data()).isEqualTo(expected);
        verify(assistanceService).analyzeMember("SYN_CUSTOMER_001");
    }
}
