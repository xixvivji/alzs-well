import { AppShell } from "../../../components/AppShell";
import { PrivateProductCenter } from "../../../components/PrivateProductCenter";

export default function ProductsPage() {
  return <AppShell mode="customer" title="금융상품·자산"><PrivateProductCenter /></AppShell>;
}
