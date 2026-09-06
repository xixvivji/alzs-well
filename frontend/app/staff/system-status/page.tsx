import { AppShell } from "../../../components/AppShell";
import { SystemStatusDashboard } from "../../../components/SystemStatusDashboard";

export default function SystemStatusPage() {
  return <AppShell mode="staff" title="서비스 상태"><SystemStatusDashboard /></AppShell>;
}
