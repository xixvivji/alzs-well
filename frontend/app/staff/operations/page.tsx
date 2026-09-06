import { AppShell } from "../../../components/AppShell";
import { StaffWorkOverview } from "../../../components/StaffWorkOverview";

export default function StaffOperationsPage() {
  return <AppShell mode="staff" staffRole="protection" title="업무 현황"><StaffWorkOverview /></AppShell>;
}
