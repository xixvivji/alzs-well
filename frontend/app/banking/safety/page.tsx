import { redirect } from "next/navigation";

export default async function BankingSafetyPage({ searchParams }: { searchParams: Promise<{ alertId?: string }> }) {
  const { alertId } = await searchParams;
  redirect(`/banking/help${typeof alertId === "string" ? `?alertId=${encodeURIComponent(alertId)}` : ""}#help-analysis`);
}
