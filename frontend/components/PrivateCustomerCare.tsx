"use client";

import { useEffect, useState } from "react";
import {
  createTrustedContact, ensureTrustedContactConsent, loadPrivateCustomerCare,
  revokeTrustedContact, submitAlertAppeal, updateAccessibilitySettings,
  updateCustomerDisplayName, updateCustomerPreferences,
  type AlertAppeal, type CustomerCareBundle, type TrustedContact,
} from "../lib/private-customer-care";
import {
  loginPrivateCustomer, logoutPrivateCustomer, type PrivateCustomerSession,
} from "../lib/private-financial-products";

type CareTab = "profile" | "contact" | "appeal";
type Busy = "login" | "load" | "profile" | "preferences" | "accessibility" | "consent" | "contact" | "logout" | string | null;

export function PrivateCustomerCare() {
  const [loginId, setLoginId] = useState("synthetic-customer");
  const [password, setPassword] = useState("");
  const [session, setSession] = useState<PrivateCustomerSession | null>(null);
  const [bundle, setBundle] = useState<CustomerCareBundle | null>(null);
  const [tab, setTab] = useState<CareTab>("profile");
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [contactName, setContactName] = useState("가족 보호자");
  const [relationship, setRelationship] = useState("FAMILY");
  const [maskedContact, setMaskedContact] = useState("010-****-1234");
  const [appealStatement, setAppealStatement] = useState("금융활동의 생활맥락이 충분히 반영되지 않아 사람의 재검토를 요청합니다.");
  const [appeals, setAppeals] = useState<Record<string, AlertAppeal>>({});

  useEffect(() => {
    if (!bundle) return;
    setDisplayName(bundle.summary.displayName);
    applyAccessibility(bundle.accessibility.largeFont, bundle.accessibility.highContrast);
  }, [bundle]);

  async function refresh(active = session) {
    if (!active) return;
    setBundle(await loadPrivateCustomerCare(active));
  }

  async function login() {
    setBusy("login"); clearNotice();
    let authenticated: PrivateCustomerSession | null = null;
    try {
      authenticated = await loginPrivateCustomer(loginId.trim(), password);
      setSession(authenticated); setPassword(""); setBusy("load");
      await refresh(authenticated);
    } catch (reason) {
      if (authenticated) await logoutPrivateCustomer(authenticated).catch(() => undefined);
      setSession(null); setBundle(null); setError(loginError(reason));
    } finally { setBusy(null); }
  }

  async function logout() {
    if (!session) return;
    setBusy("logout");
    try { await logoutPrivateCustomer(session); } catch { /* 만료된 token도 메모리에서 폐기한다. */ }
    finally { setSession(null); setBundle(null); setAppeals({}); setBusy(null); }
  }

  async function saveProfile() {
    if (!session || !bundle || !displayName.trim()) return;
    await run("profile", async () => {
      await updateCustomerDisplayName(session, bundle.summary.version, displayName.trim());
      await refresh(); return "표시 이름을 안전하게 변경했습니다.";
    });
  }

  async function savePreferences() {
    if (!session || !bundle) return;
    await run("preferences", async () => {
      await updateCustomerPreferences(session, bundle.preferences); await refresh();
      return "알림 수신 설정을 반영했습니다. 외부 알림 발송은 실행하지 않았습니다.";
    });
  }

  async function saveAccessibility() {
    if (!session || !bundle) return;
    await run("accessibility", async () => {
      await updateAccessibilitySettings(session, bundle.accessibility); await refresh();
      return "접근성 설정을 계정에 저장하고 현재 화면에 적용했습니다.";
    });
  }

  async function prepareConsent() {
    if (!session || !bundle) return;
    await run("consent", async () => {
      await ensureTrustedContactConsent(session, bundle.consents); await refresh();
      return "신뢰 연락처 최소정보 동의를 준비했습니다.";
    });
  }

  async function addContact() {
    if (!session || !bundle) return;
    await run("contact", async () => {
      const consent = await ensureTrustedContactConsent(session, bundle.consents);
      await createTrustedContact(session, consent.consentId, {
        displayName: contactName.trim(), relationshipCode: relationship, maskedContact,
        scopes: ["ALERT_REASON_SUMMARY", "CONTACT_REQUEST_STATUS"],
      });
      await refresh(); return "신뢰 연락처를 수락 대기 상태로 등록했습니다. 실제 연락은 보내지 않았습니다.";
    });
  }

  async function revoke(contact: TrustedContact) {
    if (!session) return;
    await run(`revoke:${contact.contactId}`, async () => {
      await revokeTrustedContact(session, contact, "고객이 신뢰 연락처 지정을 직접 철회했습니다.");
      await refresh(); return "신뢰 연락처 지정을 철회했습니다.";
    });
  }

  async function appeal(alertId: string) {
    if (!session || !bundle || !appealStatement.trim()) return;
    const alert = bundle.alerts.find((item) => item.alertId === alertId);
    if (!alert) return;
    await run(`appeal:${alertId}`, async () => {
      const result = await submitAlertAppeal(session, alert, "REQUEST_HUMAN_REVIEW", appealStatement.trim());
      setAppeals((current) => ({ ...current, [alertId]: result }));
      await refresh(); return "사람의 재검토 요청을 접수했습니다. 자동 금융조치는 실행되지 않았습니다.";
    });
  }

  async function run(action: Exclude<Busy, null>, task: () => Promise<string>) {
    setBusy(action); clearNotice();
    try { setMessage(await task()); } catch (reason) { setError(messageOf(reason)); }
    finally { setBusy(null); }
  }

  function clearNotice() { setError(""); setMessage(""); }

  if (!session || !bundle) return <section className="panel private-login-panel customer-care-login">
    <div className="private-login-copy"><p className="label">고객 보호 설정</p><h2>프로필과 도움 요청 범위는<br />본인 인증 후 관리합니다.</h2><p>공개 데모 capability와 운영 고객 Bearer 권한을 분리합니다. 합성 고객의 설정만 조회하며 token은 브라우저 저장소에 남기지 않습니다.</p><ul><li>큰 글씨·고대비·음성안내 계정 저장</li><li>신뢰 연락처는 대리권 없이 최소정보만 지정</li><li>AI 결과에 이의가 있으면 사람의 재검토 요청</li></ul></div>
    <form onSubmit={(event) => { event.preventDefault(); void login(); }}><label><span>합성 계정 ID</span><input autoComplete="username" value={loginId} maxLength={80} onChange={(event) => setLoginId(event.target.value)} /></label><label><span>비밀번호</span><input type="password" autoComplete="current-password" value={password} minLength={12} maxLength={200} onChange={(event) => setPassword(event.target.value)} /></label><button className="primary-button" disabled={busy !== null || !loginId.trim() || password.length < 12}>{busy === "login" || busy === "load" ? "인증·설정 조회 중…" : "사설 PoC 로그인"}</button>{error && <p className="api-error" role="alert">{error}</p>}</form>
  </section>;

  const eligibleConsent = bundle.consents.find((item) => item.purposeCode === "TRUSTED_CONTACT_DISCLOSURE" && item.status === "GRANTED" && item.scopes.includes("CONTACT_MINIMUM"));
  return <div className="customer-care-center">
    <section className="private-session-bar"><div><span>{bundle.summary.displayName.slice(0, 1)}</span><p><strong>{bundle.summary.displayName}</strong><small>{bundle.summary.organization} · {bundle.summary.customerId}</small></p></div><div><small>접근권한 {session.permissions.length}개 · token 메모리 전용</small><button onClick={() => void logout()} disabled={busy !== null}>{busy === "logout" ? "종료 중…" : "안전하게 로그아웃"}</button></div></section>
    <nav className="product-tabs care-tabs" aria-label="고객 보호 설정"><button className={tab === "profile" ? "active" : ""} onClick={() => setTab("profile")}>프로필·접근성</button><button className={tab === "contact" ? "active" : ""} onClick={() => setTab("contact")}>신뢰 연락처</button><button className={tab === "appeal" ? "active" : ""} onClick={() => setTab("appeal")}>이의신청</button></nav>

    {tab === "profile" && <div className="care-screen">
      <section className="care-hero"><div><p>나에게 맞는 금융생활 화면</p><h2>{bundle.summary.displayName}님의 이용 설정</h2><small>설정 버전 {bundle.summary.version} · {dateTime(bundle.summary.updatedAt)} 갱신</small></div><span><strong>{bundle.dataSummary.institutions}개 기관 · {bundle.dataSummary.accounts}개 계좌</strong>{bundle.dataSummary.transactionsSynced.toLocaleString("ko-KR")}건의 합성 거래 범위</span></section>
      <div className="care-two-column"><section className="panel care-form"><p className="label">표시 프로필</p><h2>화면에 보일 이름</h2><label><span>표시 이름</span><input value={displayName} maxLength={80} onChange={(event) => setDisplayName(event.target.value)} /></label><button className="primary-button" disabled={busy !== null || !displayName.trim() || displayName.trim() === bundle.summary.displayName} onClick={() => void saveProfile()}>{busy === "profile" ? "저장 중…" : "표시 이름 저장"}</button></section>
      <section className="panel care-form"><p className="label">알림 수신</p><h2>알림을 받을 채널</h2><div className="setting-toggle-list">{([["smsNotificationEnabled", "문자 알림", "실제 발송은 비활성화"], ["pushNotificationEnabled", "앱 푸시", "기기 연동 전 설정만 저장"], ["inAppNotificationEnabled", "앱 안 알림", "쉬운말 확인 화면 제공"]] as const).map(([key, title, detail]) => <Toggle key={key} checked={bundle.preferences[key]} title={title} detail={detail} onChange={(checked) => setBundle({ ...bundle, preferences: { ...bundle.preferences, [key]: checked } })} />)}</div><button className="primary-button" disabled={busy !== null} onClick={() => void savePreferences()}>{busy === "preferences" ? "저장 중…" : "알림 설정 저장"}</button></section></div>
      <section className="panel accessibility-workspace"><div className="section-heading"><div><p className="label">접근성 개인화</p><h2>읽고 누르기 편한 화면</h2></div><span className="status-chip">계정 설정 v{bundle.accessibility.version}</span></div><div className="accessibility-option-grid">{([["largeFont", "큰 글씨", "본문과 버튼을 더 크게 표시"], ["highContrast", "고대비", "색상 대비를 높여 구분"], ["speechGuidance", "음성 안내", "쉬운말 문장을 읽어주는 설정"], ["oneHandMode", "한 손 모드", "주요 행동을 화면 아래에 배치"]] as const).map(([key, title, detail]) => <Toggle key={key} checked={bundle.accessibility[key]} title={title} detail={detail} onChange={(checked) => setBundle({ ...bundle, accessibility: { ...bundle.accessibility, [key]: checked } })} />)}</div><div className="care-action-row"><p>음성 설정은 사용 의향만 저장하며 브라우저 음성 권한을 자동 요청하지 않습니다.</p><button className="primary-button" disabled={busy !== null} onClick={() => void saveAccessibility()}>{busy === "accessibility" ? "적용 중…" : "접근성 저장·적용"}</button></div></section>
      <section className="panel data-scope-card"><div><p className="label">내 데이터 범위</p><h2>어떤 합성 정보가 연결되어 있나요?</h2></div><dl><div><dt>계좌</dt><dd>{bundle.dataSummary.dataFreshness.accounts}</dd></div><div><dt>거래</dt><dd>{bundle.dataSummary.dataFreshness.transactions}</dd></div><div><dt>장기 기준선</dt><dd>{bundle.dataSummary.dataFreshness.baseline}</dd></div><div><dt>마지막 동기화</dt><dd>{bundle.dataSummary.lastSyncAt ? dateTime(bundle.dataSummary.lastSyncAt) : "고정 snapshot"}</dd></div></dl></section>
    </div>}

    {tab === "contact" && <div className="care-screen">
      <section className="trusted-contact-hero"><div><p>고객 동의 기반 최소정보</p><h2>신뢰 연락처</h2><small>도움을 요청할 사람을 지정하되 금융행위 대리권은 부여하지 않습니다.</small></div><span className={eligibleConsent ? "ready" : "pending"}>{eligibleConsent ? "최소정보 동의 준비됨" : "최소정보 동의 필요"}</span></section>
      <div className="care-two-column"><section className="panel contact-form"><div className="section-heading"><div><p className="label">새 연락처 지정</p><h2>수락 대기로 등록</h2></div><span className="status-chip">외부 연락 없음</span></div>{!eligibleConsent && <div className="consent-prerequisite"><p>먼저 `CONTACT_MINIMUM` 범위의 목적별 동의가 필요합니다.</p><button className="secondary-button" disabled={busy !== null} onClick={() => void prepareConsent()}>{busy === "consent" ? "동의 준비 중…" : "최소정보 동의 만들기"}</button></div>}<label><span>표시 이름</span><input value={contactName} maxLength={80} onChange={(event) => setContactName(event.target.value)} /></label><label><span>관계</span><select value={relationship} onChange={(event) => setRelationship(event.target.value)}><option value="FAMILY">가족</option><option value="CAREGIVER">돌봄 제공자</option><option value="OTHER">기타</option></select></label><label><span>마스킹 연락처</span><input value={maskedContact} pattern="[0-9+]{2,4}-\*{3,8}-[0-9]{2,4}" onChange={(event) => setMaskedContact(event.target.value)} /></label><button className="primary-button" disabled={busy !== null || !contactName.trim() || !eligibleConsent} onClick={() => void addContact()}>{busy === "contact" ? "등록 중…" : "신뢰 연락처 지정"}</button></section>
      <section className="panel contact-list"><div className="section-heading"><div><p className="label">현재 지정</p><h2>{bundle.contacts.length}명의 신뢰 연락처</h2></div></div>{bundle.contacts.length ? bundle.contacts.map((contact) => <article key={contact.contactId}><div><span>{relationshipLabel(contact.relationshipCode)}</span><strong>{contact.displayName}</strong><small>{contact.maskedContact} · {contact.acceptanceStatus}</small></div><ul>{contact.scopes.map((scope) => <li key={scope}>{scopeLabel(scope)}</li>)}</ul><p>대리권 없음 · 외부 연락 {contact.externalContactEnabled ? "허용" : "비활성"} · {date(contact.expiresAt)}까지</p><button disabled={busy !== null} onClick={() => void revoke(contact)}>{busy === `revoke:${contact.contactId}` ? "철회 중…" : "지정 철회"}</button></article>) : <div className="empty-block">등록된 신뢰 연락처가 없습니다.</div>}</section></div>
    </div>}

    {tab === "appeal" && <div className="care-screen">
      <section className="appeal-hero"><div><p>AI·규칙 결과에 대한 권리</p><h2>사람의 재검토를 요청할 수 있습니다.</h2><small>재검토 요청은 사건만 생성하며 계좌 제한이나 외부 연락을 자동 실행하지 않습니다.</small></div><span>고객 직접 요청 · 추가 전용 감사이력</span></section>
      <section className="panel appeal-workspace"><label><span>재검토 요청 내용</span><textarea value={appealStatement} maxLength={300} onChange={(event) => setAppealStatement(event.target.value)} /></label><div className="appeal-list">{bundle.alerts.length ? bundle.alerts.map((alert) => { const result = appeals[alert.alertId]; return <article key={alert.alertId}><div><span className={`status-pill ${alert.severity === "HIGH" ? "warning" : "safe"}`}>{severityLabel(alert.severity)}</span><div><strong>{reasonLabel(alert.reasonCode)}</strong><small>{alert.state} · 알림 v{alert.version} · {dateTime(alert.updatedAt)}</small></div></div><p>{result ? `재검토 ${result.status} · 사건 ${result.caseId}` : "자동 판정을 수용하기 어렵거나 생활맥락이 빠졌다면 사람이 다시 확인합니다."}</p><button className="primary-button" disabled={busy !== null || Boolean(result) || !appealStatement.trim()} onClick={() => void appeal(alert.alertId)}>{busy === `appeal:${alert.alertId}` ? "접수 중…" : result ? "재검토 접수 완료" : "사람 재검토 요청"}</button></article>; }) : <div className="empty-block">현재 이의신청할 운영 알림이 없습니다.</div>}</div></section>
    </div>}
    {message && <p className="workflow-result" role="status">{message}</p>}{error && <p className="api-error" role="alert">{error}</p>}
  </div>;
}

function Toggle({ checked, title, detail, onChange }: { checked: boolean; title: string; detail: string; onChange: (checked: boolean) => void }) {
  return <label className={checked ? "setting-toggle selected" : "setting-toggle"}><input aria-label={title} type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} /><span aria-hidden="true"><i /></span><div><strong>{title}</strong><small>{detail}</small></div></label>;
}
function applyAccessibility(largeFont: boolean, highContrast: boolean) {
  document.documentElement.dataset.largeText = String(largeFont);
  document.documentElement.dataset.highContrast = String(highContrast);
  localStorage.setItem("alzs-large-text", String(largeFont));
  localStorage.setItem("alzs-high-contrast", String(highContrast));
}
function date(value: string) { return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value)); }
function dateTime(value: string) { return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function relationshipLabel(value: string) { return ({ FAMILY: "가족", CAREGIVER: "돌봄 제공자", OTHER: "기타" } as Record<string, string>)[value] ?? value; }
function scopeLabel(value: string) { return ({ ALERT_REASON_SUMMARY: "변화 이유 요약", CONTACT_REQUEST_STATUS: "도움 요청 상태", PROTECTION_GUIDANCE_SUMMARY: "보호 안내 요약" } as Record<string, string>)[value] ?? value; }
function reasonLabel(value: string) { return ({ MISSED_RECURRING: "정기납부 누락 증가", DUPLICATE_TRANSFER: "중복송금 증가", REPEATED_CONFIRMATION: "거래결과 반복 확인 증가" } as Record<string, string>)[value] ?? value; }
function severityLabel(value: string) { return ({ LOW: "낮음", MEDIUM: "주의", HIGH: "높음" } as Record<string, string>)[value] ?? value; }
function messageOf(reason: unknown) { return reason instanceof Error ? reason.message : "고객 보호 설정을 처리하지 못했습니다."; }
function loginError(reason: unknown) { const message = messageOf(reason); return /404|찾을 수|등록되지/.test(message) ? "공개 production에서는 로컬 합성 로그인이 비활성화됩니다. 사설 staging의 기업 IdP 연결 상태를 확인해 주세요." : message; }
