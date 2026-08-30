import { AppShell } from "../../components/AppShell";
import { DemoDashboard } from "../../components/DemoDashboard";
import { ScenarioDatasetSummary } from "../../components/ScenarioDatasetSummary";
export default function DemoPage() { return <AppShell mode="customer" title="내 금융생활"><DemoDashboard /><ScenarioDatasetSummary /></AppShell>; }
