"use client";

import { useEffect, useState } from "react";

export function AccessibilityControls() {
  const [largeText, setLargeText] = useState(false);
  const [highContrast, setHighContrast] = useState(false);

  useEffect(() => {
    const large = localStorage.getItem("alzs-large-text") === "true";
    const contrast = localStorage.getItem("alzs-high-contrast") === "true";
    setLargeText(large); setHighContrast(contrast);
    document.documentElement.dataset.largeText = String(large);
    document.documentElement.dataset.highContrast = String(contrast);
  }, []);

  function toggleLargeText() {
    const next = !largeText; setLargeText(next);
    localStorage.setItem("alzs-large-text", String(next));
    document.documentElement.dataset.largeText = String(next);
  }

  function toggleContrast() {
    const next = !highContrast; setHighContrast(next);
    localStorage.setItem("alzs-high-contrast", String(next));
    document.documentElement.dataset.highContrast = String(next);
  }

  function moveToTop() {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.scrollTo({ top: 0, behavior: reduceMotion ? "auto" : "smooth" });
  }

  return <aside className="floating-view-tools" id="floating-view-tools" tabIndex={-1} aria-label="화면 보기 도구">
    <div className="accessibility-controls" role="group" aria-label="화면 보기 설정">
      <button type="button" aria-pressed={largeText} aria-label={largeText ? "글자를 기본 크기로 줄이기" : "글자를 크게 보기"} onClick={toggleLargeText}>
        <span className="view-tool-icon view-tool-letter" aria-hidden="true">가{largeText ? "−" : "+"}</span>
        <span>{largeText ? "글자 작게" : "글자 크게"}</span>
      </button>
      <button type="button" aria-pressed={highContrast} aria-label={`선명한 화면 ${highContrast ? "끄기" : "켜기"}`} onClick={toggleContrast}>
        <span className="view-tool-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" /><path d="M12 4a8 8 0 0 1 0 16Z" /></svg></span>
        <span>선명하게</span>
      </button>
    </div>
    <button className="back-to-top" type="button" aria-label="화면 맨 위로 이동" onClick={moveToTop}>
      <span className="view-tool-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m7 11 5-5 5 5M12 6v12" /></svg></span>
      <span>맨 위로</span>
    </button>
    <span className="visually-hidden" aria-live="polite">큰 글씨 {largeText ? "사용 중" : "사용 안 함"}, 선명한 화면 {highContrast ? "사용 중" : "사용 안 함"}</span>
  </aside>;
}
