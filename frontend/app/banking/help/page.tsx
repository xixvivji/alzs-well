import { BankingShell } from "../../../components/BankingShell";
import { PrivateHelpHub } from "../../../components/PrivateHelpHub";
import "./banking-help.css";
import "./member-analysis.css";

export default function BankingHelpPage() {
  return <BankingShell title="금융생활 도움받기" description="로그인한 회원의 합성 금융데이터로 의향, 장기 변화와 확인할 내용을 연결합니다."><PrivateHelpHub /></BankingShell>;
}
