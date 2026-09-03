import { AppShell } from "../../../components/AppShell";
import { PrivateCustomerCare } from "../../../components/PrivateCustomerCare";

export default function CustomerSettingsPage() {
  return <AppShell mode="customer" title="내 정보·도움 설정"><PrivateCustomerCare /></AppShell>;
}
