import { ApiServiceCatalog } from "../../../components/ApiServiceCatalog";
import { AppShell } from "../../../components/AppShell";

export default function StaffOperationsPage() {
  return <AppShell mode="staff" title="보호업무 운영"><ApiServiceCatalog mode="staff" /></AppShell>;
}
