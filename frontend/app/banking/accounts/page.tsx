import { BankingShell } from "../../../components/BankingShell";
import { PrivateAccountsWorkspace } from "../../../components/PrivateAccountsWorkspace";

export default function BankingAccountsPage() {
  return <BankingShell title="계좌·거래" description="회원별 계좌, 거래내역, 정기납부와 잔액 추세를 조회합니다."><PrivateAccountsWorkspace /></BankingShell>;
}
