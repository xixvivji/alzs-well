package com.alzswell.copilot.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeterministicCopilotAdapter implements CopilotPort {
    @Override
    public CopilotDraft generate(CopilotFacts facts) {
        return new CopilotDraft(
                "고객이 일부 금융생활 변화를 확인하기 어려워 행원의 추가 사실확인이 필요합니다.",
                List.of(
                        "최근 정기납부가 처리되지 않은 이유를 함께 확인해도 될까요?",
                        "짧은 시간 안에 같은 금액을 두 번 송금한 사유가 있었을까요?",
                        "거래 완료 후 결과를 여러 번 확인하게 된 불편이 있었을까요?"
                ),
                List.of("정기납부 처리상태 확인", "중복송금 취소·환불 여부 확인", "거래 결과화면 지연 여부 확인"),
                List.copyOf(facts.reasonCodes()),
                "DETERMINISTIC_TEMPLATE", true, false, false,
                "NONE", List.of()
        );
    }
}
