package com.alzswell.copilot.application;

import com.alzswell.casework.application.OperationalCaseService;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.common.security.AuditActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OperationalCopilotService {
    private final OperationalCaseService cases;
    private final CopilotPort copilot;

    public OperationalCopilotService(OperationalCaseService cases, CopilotPort copilot) {
        this.cases = cases;
        this.copilot = copilot;
    }

    public CopilotPort.CopilotDraft generate(UUID caseId, AuditActor actor) {
        var detail = cases.detail(caseId, actor);
        var evidence = cases.evidence(caseId, actor);
        var draft = copilot.generate(new CopilotPort.CopilotFacts("CONSULTATION_NOTE",
                detail.customerResponseCode(), List.of(detail.reasonCode()), List.of(), evidence.syntheticData()));
        // No database transaction is held during inference. Recheck access and facts before returning.
        var current = cases.detail(caseId, actor);
        if (!current.equals(detail)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "사건이 변경되었습니다. 최신 내용을 확인한 후 다시 생성해 주세요.");
        }
        return draft;
    }
}
