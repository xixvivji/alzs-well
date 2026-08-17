"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, type ApiResponse } from "../lib/api";
import { clearDemoContext, readDemoContext, saveDemoContext, type DemoContext } from "../lib/demo-session";
type Created={sessionId:string}; type Ingested={demoRunId:string;customerId:string;alertId:string};
type Summary={assets:{total:{amount:string}};cashFlow:{monthlyIncome:{amount:string};monthlyExpense:{amount:string}};changeSummary:{openAlertCount:number;summary:string}};
type AlertList={items?:Array<Record<string,unknown>>};
const messageOf=(error:unknown)=>(error as Partial<ApiResponse<unknown>>).message??(error instanceof Error?error.message:"백엔드 연결 상태를 확인해 주세요.");
export function DemoDashboard(){
 const[context,setContext]=useState<DemoContext|null>(null),[summary,setSummary]=useState<Summary|null>(null),[alertCount,setAlertCount]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState("");
 async function load(active:DemoContext){const common={capability:active.capability,demoRunId:active.demoRunId};const[s,a]=await Promise.all([apiRequest<Summary>(`/api/v1/demo/sessions/${active.sessionId}/customers/${active.customerId}/financial-summary`,common),apiRequest<AlertList>(`/api/v1/demo/sessions/${active.sessionId}/customers/${active.customerId}/alerts`,common)]);setSummary(s.body.data);setAlertCount(a.body.data?.items?.length??s.body.data?.changeSummary.openAlertCount??0)}
 useEffect(()=>{const saved=readDemoContext();if(saved){setContext(saved);load(saved).catch(reason=>setError(messageOf(reason))) }},[]);
 async function startDemo(){setLoading(true);setError("");try{const created=await apiRequest<Created>("/api/v1/demo/sessions",{method:"POST"});const sessionId=created.body.data?.sessionId,capability=created.headers.get("X-Demo-Customer-Capability");if(!sessionId||!capability)throw new Error("세션 발급 응답을 확인해 주세요.");const ingested=await apiRequest<Ingested>(`/api/v1/demo/sessions/${sessionId}/scenarios/FIN_MGMT_AB_001/ingest`,{method:"POST",capability,idempotencyKey:crypto.randomUUID()});if(!ingested.body.data)throw new Error("시나리오 적재 응답을 확인해 주세요.");const next={sessionId,capability,demoRunId:ingested.body.data.demoRunId,customerId:ingested.body.data.customerId,alertId:ingested.body.data.alertId};saveDemoContext(next);setContext(next);await load(next)}catch(reason){setError(messageOf(reason))}finally{setLoading(false)}}
 function reset(){clearDemoContext();setContext(null);setSummary(null);setAlertCount(0);setError("")}
 const money=(value?:string)=>value?`${Number(value).toLocaleString("ko-KR")}원`:"-";
 if(!context)return <><section className="panel hero-panel"><div><p className="muted">합성데이터 기반 안전 체험</p><h2>금융생활 변화 알림을<br/>직접 확인해 보세요.</h2></div><button onClick={startDemo} disabled={loading}>{loading?"준비 중…":"서비스 체험 시작"}</button></section>{error&&<p className="api-error" role="alert">{error}</p>}</>;
 return <><section className="summary-strip"><article className="panel"><p className="label">총 금융자산</p><strong>{money(summary?.assets.total.amount)}</strong></article><article className="panel"><p className="label">월 수입</p><strong>{money(summary?.cashFlow.monthlyIncome.amount)}</strong></article><article className="panel"><p className="label">월 지출</p><strong>{money(summary?.cashFlow.monthlyExpense.amount)}</strong></article></section><section className="panel alert-overview"><div><p className="label">최근 변화 알림</p><h2>{alertCount}개의 변화가 발견되었습니다.</h2><p className="muted">{summary?.changeSummary.summary??"고객의 확인이 필요한 금융생활 변화입니다."}</p></div><Link className="primary-button" href="/demo/alerts">알림 확인하기</Link></section>{error&&<p className="api-error" role="alert">{error}</p>}<button className="secondary-button" onClick={reset}>새 체험 시작</button></>;
}
