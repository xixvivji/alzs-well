import Link from "next/link";

export default function NotFound() {
  return <main className="recovery-page" id="app-main">
    <section aria-labelledby="not-found-title">
      <p>404 · 페이지를 찾을 수 없습니다</p>
      <h1 id="not-found-title">요청한 화면이 없거나 이동되었습니다.</h1>
      <span>주소를 다시 확인하거나 아래 버튼으로 금융서비스 첫 화면으로 이동해 주세요.</span>
      <div><Link href="/">처음 화면으로</Link><Link className="secondary" href="/help">도움 안내 보기</Link></div>
    </section>
  </main>;
}
