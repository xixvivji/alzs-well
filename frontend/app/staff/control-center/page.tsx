import { ApiServiceCatalog } from "../../../components/ApiServiceCatalog";
import { AppShell } from "../../../components/AppShell";

export default function ControlCenterPage() {
  return <AppShell mode="staff" title="관리·준법 통제센터"><ApiServiceCatalog mode="admin" /></AppShell>;
}
