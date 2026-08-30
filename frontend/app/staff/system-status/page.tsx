import { AppShell } from "../../../components/AppShell";
import { SystemStatusDashboard } from "../../../components/SystemStatusDashboard";

export default function SystemStatusPage() {
  return <AppShell mode="staff" title="시스템·AI 상태"><SystemStatusDashboard /></AppShell>;
}
