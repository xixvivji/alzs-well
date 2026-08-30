import { AppShell } from "../../../components/AppShell";
import { CustomerProtectionCenter } from "../../../components/CustomerProtectionCenter";

export default function ProtectionCenterPage() {
  return <AppShell mode="customer" title="안심 보호센터"><CustomerProtectionCenter /></AppShell>;
}
