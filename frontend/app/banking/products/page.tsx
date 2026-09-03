import { BankingShell } from "../../../components/BankingShell";
import { PrivateProductCenter } from "../../../components/PrivateProductCenter";

export default function BankingProductsPage() {
  return <BankingShell title="금융상품·자산" description="예금·외화·연금·신탁·카드·대출·투자 정보를 조회합니다."><PrivateProductCenter /></BankingShell>;
}
