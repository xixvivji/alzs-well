"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { customerLoginFromId, staffLoginPath } from "../lib/prototype-navigation";
import { useLoginReviewContext } from "./LoginNavigationContext";

export function LoginMenu({ signedIn = false }: { signedIn?: boolean }) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const id = useId();
  const review = useLoginReviewContext();
  const customerLogin = customerLoginFromId(review?.customerId);
  const customerNext = review?.alertId ? `/banking/help?alertId=${encodeURIComponent(review.alertId)}` : "/banking";
  const personalPath = `/login?switch=1&next=${encodeURIComponent(customerNext)}${customerLogin ? `&loginId=${customerLogin}` : ""}`;
  const operatorPath = `${staffLoginPath(review?.caseId, review?.customerId)}&switch=1`;

  useEffect(() => {
    if (!open) return;
    const close = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") { event.preventDefault(); setOpen(false); trigger.current?.focus(); } };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("pointerdown", close); document.removeEventListener("keydown", escape); };
  }, [open]);

  return <div className="portal-login-menu" ref={root} role="group" aria-label="로그인 메뉴"
    onPointerEnter={(event) => { if (event.pointerType === "mouse") setOpen(true); }}
    onPointerLeave={(event) => { if (event.pointerType === "mouse" && !root.current?.contains(document.activeElement)) setOpen(false); }}
    onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false); }}>
    <button ref={trigger} className="portal-login-trigger btn btn-primary" aria-expanded={open} aria-controls={id}
      onClick={() => setOpen((value) => !value)} onKeyDown={(event) => { if (event.key === "ArrowDown") { event.preventDefault(); setOpen(true); requestAnimationFrame(() => root.current?.querySelector<HTMLAnchorElement>("nav a")?.focus()); } }}>
      {signedIn ? "로그인 전환" : "로그인"}<svg aria-hidden="true" width="14" height="14" viewBox="0 0 20 20" fill="none"><path d="m5 7.5 5 5 5-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
    </button>
    <nav id={id} className="portal-login-options" aria-label="로그인 유형" hidden={!open}>
      <Link href={personalPath} onClick={() => setOpen(false)}>개인 로그인</Link>
      <Link href={operatorPath} onClick={() => setOpen(false)}>운영자 로그인</Link>
    </nav>
  </div>;
}
