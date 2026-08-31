import type { Metadata } from "next";
import "./globals.css";
import "./app-shell.css";
import "./hero-video.css";
import "./demo-flow.css";
import "./alert-detail.css";
import "./intro-film.css";
import "./senior-customer.css";
import "./staff-case-detail.css";
import "./staff-case-queue.css";
import "./ai-financial-assistant.css";
import "./financial-portal.css";
import "./extended-workflows.css";
import "./operations-portal.css";
import "./scenario-dataset.css";
import "./customer-protection-center.css";

function metadataOrigin(): URL {
  const configured = process.env.NEXT_PUBLIC_SITE_URL;
  const vercelHost = process.env.VERCEL_PROJECT_PRODUCTION_URL;
  try { return new URL(configured ?? (vercelHost ? `https://${vercelHost}` : "http://localhost:3000")); }
  catch { return new URL("http://localhost:3000"); }
}

export const metadata: Metadata = {
  metadataBase: metadataOrigin(),
  title: "ALZ's well | 금융생활의 작은 변화, 먼저 알아차리도록",
  description: "고령 금융소비자의 생활 변화를 설명하고 고객 확인에서 행원 보호업무까지 연결하는 금융 AI 서비스",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
  openGraph: {
    type: "website",
    locale: "ko_KR",
    title: "ALZ's well | 금융생활 안심 동행",
    description: "금융생활의 작은 변화, 먼저 알아차리도록.",
    images: [{ url: "/og.png", width: 1200, height: 630, alt: "ALZ's well 금융생활 안심 동행" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "ALZ's well | 금융생활 안심 동행",
    description: "금융생활의 작은 변화, 먼저 알아차리도록.",
    images: ["/og.png"],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" data-scroll-behavior="smooth"><body>{children}</body></html>
  );
}
