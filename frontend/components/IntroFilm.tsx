"use client";

import { useEffect, useState } from "react";

const INTRO_DURATION_SECONDS = 15;
const INTRO_SEEN_KEY = "alzs-well-intro-seen";

export function IntroFilm() {
  const [visible, setVisible] = useState(false);
  const [remaining, setRemaining] = useState(INTRO_DURATION_SECONDS);

  useEffect(() => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduceMotion || sessionStorage.getItem(INTRO_SEEN_KEY) === "true") return;
    setVisible(true);

    const startedAt = Date.now();
    const interval = window.setInterval(() => {
      const elapsed = Math.floor((Date.now() - startedAt) / 1000);
      setRemaining(Math.max(0, INTRO_DURATION_SECONDS - elapsed));
    }, 250);
    const timeout = window.setTimeout(() => finish(), INTRO_DURATION_SECONDS * 1000);
    return () => { window.clearInterval(interval); window.clearTimeout(timeout); };
  }, []);

  function finish() {
    sessionStorage.setItem(INTRO_SEEN_KEY, "true");
    setVisible(false);
  }

  if (!visible) return null;

  return <section className="intro-film" aria-label="ALZ's well 소개 영상">
    <video autoPlay muted playsInline preload="auto" onEnded={finish} aria-hidden="true">
      <source src="/intro-film.webm" type="video/webm" />
      <source src="/intro-film.mp4" type="video/mp4" />
    </video>
    <div className="intro-film-fallback" />
    <div className="intro-film-shade" />
    <div className="intro-film-brand"><strong>ALZ&apos;s well</strong><p>금융생활의 작은 변화를 먼저 알아차립니다.</p></div>
    <button type="button" onClick={finish} aria-label={`소개 영상 건너뛰기, 약 ${remaining}초 남음`}>
      건너뛰기 <span>{remaining}초</span>
    </button>
  </section>;
}
