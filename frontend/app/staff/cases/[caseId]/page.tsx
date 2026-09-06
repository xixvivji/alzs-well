import { redirect } from "next/navigation";

export default async function StaffCaseDetailPage({ params }: { params: Promise<{ caseId: string }> }) {
  const { caseId } = await params;
  redirect(`/staff/cases?caseId=${encodeURIComponent(caseId)}`);
}
