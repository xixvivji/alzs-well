package com.alzswell.copilot.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alzswell.casework.api.CaseworkResponses.*;
import com.alzswell.casework.application.OperationalCaseService;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalCopilotServiceTest {
    private final OperationalCaseService cases = mock(OperationalCaseService.class);
    private final CopilotPort port = mock(CopilotPort.class);
    private final UUID id = UUID.randomUUID();
    private final OperationalCopilotService service = new OperationalCopilotService(cases, port);

    @Test
    void rejectsUnauthorizedBeforeGeneration() {
        when(cases.detail(id, null)).thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.generate(id, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(port);
    }

    @Test
    void rechecksAccessAndStateAfterGeneration() {
        var detail = detail("UNSURE");
        when(cases.detail(id, null)).thenReturn(detail, detail("KNOWN"));
        when(cases.evidence(id, null)).thenReturn(new CaseEvidence(id, id, "DUPLICATE_TRANSFER", "1", "3", "건", List.of(), 0, true));
        assertThatThrownBy(() -> service.generate(id, null)).isInstanceOf(BusinessException.class);
        verify(port).generate(new CopilotPort.CopilotFacts("CONSULTATION_NOTE", "UNSURE", List.of("DUPLICATE_TRANSFER"), List.of(), true));
        verify(cases, times(2)).detail(id, null);
    }

    @Test
    void returnsDraftWithoutMutatingCase() {
        when(cases.detail(id, null)).thenReturn(detail("UNSURE"));
        when(cases.evidence(id, null)).thenReturn(new CaseEvidence(id, id, "DUPLICATE_TRANSFER", "1", "3", "건", List.of(), 0, true));
        var draft = new DeterministicCopilotAdapter().generate(new CopilotPort.CopilotFacts("CONSULTATION_NOTE", "UNSURE", List.of(), List.of()));
        when(port.generate(any())).thenReturn(draft);
        assertThat(service.generate(id, null)).isSameAs(draft);
        verify(cases, times(2)).detail(id, null);
        verify(cases).evidence(id, null);
        verifyNoMoreInteractions(cases);
    }

    private CaseDetail detail(String response) {
        return new CaseDetail(null, "BANK_REVIEW", "DUPLICATE_TRANSFER", "HIGH", response, null, List.of(), false, false);
    }
}
