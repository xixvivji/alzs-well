import { AppShell } from "../../../../components/AppShell";
import { AlertDetail } from "../../../../components/AlertDetail";
export default async function AlertDetailPage({params}:{params:Promise<{alertId:string}>}){const{alertId}=await params;return <AppShell mode="customer" title="변화 알림 상세"><AlertDetail alertId={alertId}/></AppShell>}
