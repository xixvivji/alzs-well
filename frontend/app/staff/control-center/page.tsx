import { AppShell } from "../../../components/AppShell";
import { OperationalRoleDashboard } from "../../../components/OperationalRoleDashboard";

export default function ControlCenterPage() {
  return <AppShell mode="staff" staffRole="admin" title="관리·준법"><OperationalRoleDashboard mode="admin" /></AppShell>;
}
