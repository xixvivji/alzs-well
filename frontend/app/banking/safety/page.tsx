import { BankingShell } from "../../../components/BankingShell";
import { PrivateSafetyCenter } from "../../../components/PrivateSafetyCenter";

export default function BankingSafetyPage() {
  return <BankingShell title="금융생활 안심관리" description="평소 기준선, 장기 변화, 본인 확인과 판단 이력을 한곳에서 확인합니다."><PrivateSafetyCenter /></BankingShell>;
}
