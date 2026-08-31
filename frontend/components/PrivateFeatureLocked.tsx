import Link from "next/link";

export function PrivateFeatureLocked({ title }: { title: string }) {
  return <section className="panel private-feature-locked" role="status">
    <span aria-hidden="true">⌁</span>
    <p className="label">PRIVATE POC ONLY</p>
    <h2>{title}은 공개 서비스에서 잠겨 있습니다.</h2>
    <p>이 화면은 운영 고객정보를 다루므로 기업 IdP·MFA가 연결된 사설 staging에서만 엽니다. 공개 Vercel production에서는 메뉴와 기능을 모두 숨기며, URL로 직접 접근해도 API를 호출하지 않습니다.</p>
    <Link className="primary-button" href="/demo/services">공개 금융서비스 보기</Link>
  </section>;
}
