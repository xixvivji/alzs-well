import { BankingShell } from "../../../components/BankingShell";
import { PrivateLifeServices } from "../../../components/PrivateLifeServices";

export default function BankingLifePage() {
  return <BankingShell title="생활금융·고객센터" description="금융생활 의향, 알림, 보호수단, 기관 연결과 고객 권리를 관리합니다."><PrivateLifeServices /></BankingShell>;
}
