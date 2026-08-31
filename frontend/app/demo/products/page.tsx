import { AppShell } from "../../../components/AppShell";
import { PrivateProductCenter } from "../../../components/PrivateProductCenter";
import { PrivateFeatureLocked } from "../../../components/PrivateFeatureLocked";

export default function ProductsPage() {
  const enabled = process.env.NEXT_PUBLIC_PRIVATE_POC_ENABLED === "true"
    && process.env.PRIVATE_POC_DEPLOYMENT_ALLOWED === "true";
  return <AppShell mode="customer" title="금융상품·자산">{enabled
    ? <PrivateProductCenter />
    : <PrivateFeatureLocked title="금융상품·자산" />}</AppShell>;
}
