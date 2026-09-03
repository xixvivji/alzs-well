import Link from "next/link";
import { AppShell } from "../../../../components/AppShell";
import { StaffCaseDetail } from "../../../../components/StaffCaseDetail";

export const dynamic = "force-dynamic";

export default async function StaffCaseDetailPage({
  params,
}: {
  params: Promise<{ caseId: string }>;
}) {
  const { caseId } = await params;
  return <AppShell mode="staff" staffRole="protection" title="보호업무 사건 상세">
    <Link className="case-back-link" href="/staff/cases">← 사건 큐로 돌아가기</Link>
    <StaffCaseDetail caseId={caseId} />
  </AppShell>;
}
