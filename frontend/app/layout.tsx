import type { Metadata } from "next";
import { AccessibilityControls } from "../components/AccessibilityControls";
import "./globals.css";
import "./bank-home.css";
import "./assistance-start.css";
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
import "./member-login.css";
import "./help-entry.css";
import "./role-portal-completion.css";
import "./accessibility-redesign.css";
import "./ux-product-redesign.css";
import "./continuity-workflow.css";
import "./portal-navigation.css";
import "./staff-review-workbench.css";

function metadataOrigin(): URL {
  const configured = process.env.NEXT_PUBLIC_SITE_URL;
  const vercelHost = process.env.VERCEL_PROJECT_PRODUCTION_URL;
  try { return new URL(configured ?? (vercelHost ? `https://${vercelHost}` : "http://localhost:3000")); }
  catch { return new URL("http://localhost:3000"); }
}

export const metadata: Metadata = {
  metadataBase: metadataOrigin(),
  title: "ALZ's well | 금융생활 안심 동행",
  description: "일상 금융서비스와 쉬운 금융생활 도움을 한곳에서 제공하는 금융생활 안심 동행 서비스",
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
    <html lang="ko" data-scroll-behavior="smooth">
      <head>
        <link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin="anonymous" />
        <link rel="stylesheet" crossOrigin="anonymous" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css" />
      </head>
      <body>
        <a className="skip-link global-view-tools-skip" href="#floating-view-tools">화면 보기 도구로 이동</a>
        {children}
        <AccessibilityControls />
      </body>
    </html>
  );
}
