import Link from "next/link";
import { AppShell } from "../../../../../components/AppShell";
import { StaffCaseDetail } from "../../../../../components/StaffCaseDetail";

export const dynamic = "force-dynamic";
export default async function DemoCaseDetailPage({ params }: { params: Promise<{ caseId: string }> }) {
  const { caseId } = await params;
  return <AppShell mode="staff" staffRole="protection" demoStaff title="시연 사건 상세"><Link className="case-back-link" href="/demo/staff/cases">사건 목록으로</Link><StaffCaseDetail caseId={caseId} /></AppShell>;
}
