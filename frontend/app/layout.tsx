import type { Metadata } from "next";
import "./globals.css";
import "./app-shell.css";
import "./hero-video.css";
import "./demo-flow.css";
import "./alert-detail.css";
import "./intro-film.css";
import "./senior-customer.css";
import "./staff-case-detail.css";
import "./ai-financial-assistant.css";

export const metadata: Metadata = {
  title: "ALZ's well | 금융생활 변화 조기알림",
  description: "금융생활의 변화를 발견하고 필요한 보호업무로 연결하는 금융안전 서비스",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko"><body>{children}</body></html>
  );
}
