import { BankingShell } from "../../components/BankingShell";
import { PrivateBankingDashboard } from "../../components/PrivateBankingDashboard";

export default function BankingPage() {
  return <BankingShell title="MY 금융" description="내 합성 계좌와 자산 흐름을 한눈에 확인합니다."><PrivateBankingDashboard /></BankingShell>;
}
