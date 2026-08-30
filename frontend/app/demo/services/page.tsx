import { ApiServiceCatalog } from "../../../components/ApiServiceCatalog";
import { AppShell } from "../../../components/AppShell";

export default function CustomerServicesPage() {
  return <AppShell mode="customer" title="금융서비스"><ApiServiceCatalog mode="customer" /></AppShell>;
}
