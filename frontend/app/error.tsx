"use client";

import Link from "next/link";
import { useEffect } from "react";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    // 상세 오류는 브라우저 화면에 노출하지 않고 배포 로그에서만 확인합니다.
    console.error("화면을 불러오지 못했습니다.", error.digest ?? "no-digest");
  }, [error]);

  return <main className="recovery-page" id="app-main">
    <section role="alert" aria-labelledby="recovery-title">
      <p>잠시 문제가 생겼습니다</p>
      <h1 id="recovery-title">화면을 불러오지 못했습니다.</h1>
      <span>입력한 내용이 있다면 다시 확인한 뒤 한 번 더 시도해 주세요.</span>
      <div>
        <button type="button" onClick={reset}>다시 시도</button>
        <Link href="/">처음 화면으로</Link>
      </div>
      {error.digest && <small>확인 번호: {error.digest}</small>}
    </section>
  </main>;
}
