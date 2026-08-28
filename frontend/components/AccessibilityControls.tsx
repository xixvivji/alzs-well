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

  return <div className="accessibility-controls" aria-label="화면 보기 설정">
    <button type="button" aria-pressed={largeText} onClick={toggleLargeText}>가<span aria-hidden="true">＋</span> 글자 크게</button>
    <button type="button" aria-pressed={highContrast} onClick={toggleContrast}>◐ 선명하게</button>
  </div>;
}
