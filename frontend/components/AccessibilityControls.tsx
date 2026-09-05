"use client";

import { useEffect, useState } from "react";

export function AccessibilityPreferences() {
  useEffect(() => {
    document.documentElement.dataset.largeText = String(localStorage.getItem("alzs-large-text") === "true");
    document.documentElement.dataset.highContrast = String(localStorage.getItem("alzs-high-contrast") === "true");
  }, []);
  return null;
}

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

  return <div className="accessibility-controls" role="group" aria-label="화면 보기 설정">
    <button type="button" aria-pressed={largeText} aria-label={`큰 글씨 ${largeText ? "끄기" : "켜기"}`} onClick={toggleLargeText}>가<span aria-hidden="true">＋</span> 글자 크게</button>
    <button type="button" aria-pressed={highContrast} aria-label={`고대비 화면 ${highContrast ? "끄기" : "켜기"}`} onClick={toggleContrast}>◐ 선명하게</button>
    <span className="visually-hidden" aria-live="polite">큰 글씨 {largeText ? "사용 중" : "사용 안 함"}, 고대비 {highContrast ? "사용 중" : "사용 안 함"}</span>
  </div>;
}
