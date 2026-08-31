import { AppShell } from "../../../components/AppShell";
import { PrivateCustomerCare } from "../../../components/PrivateCustomerCare";
import { PrivateFeatureLocked } from "../../../components/PrivateFeatureLocked";

export default function CustomerSettingsPage() {
  const enabled = process.env.NEXT_PUBLIC_PRIVATE_POC_ENABLED === "true"
    && process.env.PRIVATE_POC_DEPLOYMENT_ALLOWED === "true";
  return <AppShell mode="customer" title="내 정보·도움 설정">{enabled
    ? <PrivateCustomerCare />
    : <PrivateFeatureLocked title="내 정보·도움 설정" />}</AppShell>;
}
