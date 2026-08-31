import { AppShell } from "../../../components/AppShell";
import { FinancialWorkspace } from "../../../components/FinancialWorkspace";

export default function FinancePage() {
  return <AppShell mode="customer" title="통합 금융생활"><FinancialWorkspace /></AppShell>;
}
