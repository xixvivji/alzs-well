import { AppShell } from "../../../components/AppShell";
import { PrivateProductCenter } from "../../../components/PrivateProductCenter";

export default function ProductsPage() {
  return <AppShell mode="customer" title="카드·대출·투자"><PrivateProductCenter /></AppShell>;
}
