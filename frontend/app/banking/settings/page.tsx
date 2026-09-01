import { BankingShell } from "../../../components/BankingShell";
import { PrivateCustomerCare } from "../../../components/PrivateCustomerCare";

export default function BankingSettingsPage() {
  return <BankingShell title="내 정보·보호" description="접근성, 동의 범위, 신뢰 연락처와 재검토 권리를 관리합니다."><PrivateCustomerCare /></BankingShell>;
}
