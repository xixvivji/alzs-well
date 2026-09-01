import { suggestFinancialIntent, type IntentSuggestion } from "./ai-financial-assistance";
import { createDemoContext, discardDemoSession } from "./demo-workflow";

/** 회원의 자유 문장만 AI 구조화 경계로 전달하고 계좌·거래·회원 식별자는 전달하지 않는다. */
export async function suggestPrivateFinancialIntent(utterance: string): Promise<IntentSuggestion> {
  const context = await createDemoContext();
  try {
    return await suggestFinancialIntent(context, utterance);
  } finally {
    await discardDemoSession(context.sessionId, context.capability);
  }
}
