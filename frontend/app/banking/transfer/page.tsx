import { BankingShell } from "../../../components/BankingShell";
import { PrivateTransferWorkspace } from "../../../components/PrivateTransferWorkspace";

export default function BankingTransferPage() {
  return <BankingShell title="이체" description="실제 송금 없이 합성 잔액·한도·받는 분과 안전 조건을 확인합니다."><PrivateTransferWorkspace /></BankingShell>;
}
