package com.alzswell.copilot.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.CommonErrorCode;
import com.alzswell.demo.application.P0WorkflowService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CopilotDraftService {
    private final P0WorkflowService workflowService;
    private final CopilotPort copilotPort;

    public CopilotDraftService(P0WorkflowService workflowService, CopilotPort copilotPort) {
        this.workflowService = workflowService;
        this.copilotPort = copilotPort;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generate(UUID sessionId, UUID demoRunId, String caseId, String draftType) {
        if (!"CONSULTATION_NOTE".equals(draftType)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "draftType이 올바르지 않습니다.");
        }
        Map<String, Object> detail = workflowService.caseDetail(sessionId, demoRunId, caseId);
        Map<String, Object> alert = (Map<String, Object>) detail.get("alert");
        Map<String, Object> context = (Map<String, Object>) detail.get("customerContext");
        CopilotPort.CopilotDraft draft = copilotPort.generate(new CopilotPort.CopilotFacts(
                draftType, String.valueOf(context.get("responseCode")),
                (List<String>) alert.get("reasonCodes"), (List<String>) context.get("unconfirmedItems")
        ));
        return Map.of(
                "caseId", caseId, "demoRunId", demoRunId, "draftType", draftType, "draft", draft,
                "safety", Map.of("syntheticDataOnly", true, "containsDirectIdentifiers", false,
                        "externalActionCreated", false, "humanReviewRequired", true)
        );
    }
}
