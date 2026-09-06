import { BankingShell } from "../../../components/BankingShell";
import { PrivateHelpHub } from "../../../components/PrivateHelpHub";
import "./banking-help.css";
import "./member-analysis.css";

export default function BankingHelpPage() {
  return <BankingShell title="금융생활 도움받기" description="평소와 다른 변화를 확인하고, 내 응답과 은행 검토 연결을 한곳에서 봅니다."><PrivateHelpHub /></BankingShell>;
}
