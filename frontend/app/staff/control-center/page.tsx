import { ApiServiceCatalog } from "../../../components/ApiServiceCatalog";
import { AppShell } from "../../../components/AppShell";
import { SystemStatusDashboard } from "../../../components/SystemStatusDashboard";
import { OperationalRoleDashboard } from "../../../components/OperationalRoleDashboard";

export default function ControlCenterPage() {
  return <AppShell mode="staff" staffRole="admin" title="관리·준법 통제센터"><div className="control-center-portal">
    <section className="control-center-banner"><div><p>ADMIN &amp; COMPLIANCE</p><h2>정책과 AI가 안전 경계 안에서<br />작동하는지 확인합니다.</h2></div><ul><li>탐지 정책·알고리즘 버전 고정</li><li>합성데이터 출처와 감사 무결성</li><li>AI 검색 장애 시 안전 템플릿 폴백</li></ul></section>
    <SystemStatusDashboard />
    <OperationalRoleDashboard mode="admin" />
    <section className="admin-control-boundary panel"><div><p className="label">변경 통제</p><h2>관리 기능은 운영자 인증 후에만 실행</h2><p>정책 게시·롤백, 기능 플래그 변경, 지식 문서 승격은 공개 데모에서 실행 버튼을 제공하지 않습니다. AWS staging의 기업 IdP·MFA·승인권한 연결 후 활성화합니다.</p></div><div><span>조회</span><strong>공개 준비상태·버전</strong><span>변경</span><strong>사설 Bearer·감사기록</strong></div></section>
    <div className="operations-heading"><div><p className="label">관리·준법 API 계약</p><h2>권한별 운영 기능</h2></div><span className="status-chip">공개 실행 차단</span></div>
    <ApiServiceCatalog mode="admin" />
  </div></AppShell>;
}
