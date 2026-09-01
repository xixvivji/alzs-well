import { AppShell } from "../../components/AppShell";
import { DemoDashboard } from "../../components/DemoDashboard";
import { ScenarioDatasetSummary } from "../../components/ScenarioDatasetSummary";
export default function DemoPage() { return <AppShell mode="customer" title="내 금융생활"><DemoDashboard /><details className="scenario-data-collapsed"><summary>합성데이터 설계 보기</summary><ScenarioDatasetSummary /></details></AppShell>; }
