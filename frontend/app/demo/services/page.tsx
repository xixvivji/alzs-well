import { ApiServiceCatalog } from "../../../components/ApiServiceCatalog";
import { AppShell } from "../../../components/AppShell";

export default function CustomerServicesPage() {
  return <AppShell mode="customer" title="서비스 연결 현황"><ApiServiceCatalog mode="customer" /></AppShell>;
}
