#!/usr/bin/env python3
"""Vercel→AWS 운영 경로에서 정상·주의·오탐 합성 시나리오를 재검증한다."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any
from urllib.parse import urlparse
from uuid import uuid4


DEFAULT_BASE_URL = "https://alzs-well.vercel.app"
SCENARIO = "FIN_MGMT_AB_001"
CUSTOMER_ID = "SYN_CUSTOMER_FIN_MGMT_001"


@dataclass
class Session:
    session_id: str
    run_id: str
    alert_id: str
    staff_capability: str


class Client:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(CookieJar())
        )

    def request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> tuple[dict[str, Any], Any]:
        payload = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=payload,
            method=method,
            headers={
                "Accept": "application/json",
                **({"Content-Type": "application/json"} if payload is not None else {}),
                **(headers or {}),
            },
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                return json.load(response), response.headers
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            raise RuntimeError(f"{method} {path}: HTTP {error.code} {detail}") from None


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def command_headers(session: Session, capability: str | None = None) -> dict[str, str]:
    return {
        "X-Demo-Run-Id": session.run_id,
        **({"X-Demo-Capability": capability} if capability else {}),
    }


def idempotency(prefix: str) -> str:
    return f"prod-{prefix[:20]}-{uuid4()}"


def create_session(client: Client) -> Session:
    created, _ = client.request("POST", "/api/v1/demo/sessions")
    session_id = created["data"]["sessionId"]
    _, staff_headers = client.request(
        "POST", f"/api/internal/staff-capability/{session_id}"
    )
    staff_capability = staff_headers.get("X-Demo-Staff-Capability", "")
    require(bool(staff_capability), "직원 capability가 발급되지 않았습니다.")
    ingested, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session_id}/scenarios/{SCENARIO}/ingest",
        headers={"Idempotency-Key": idempotency("ingest")},
    )
    return Session(
        session_id=session_id,
        run_id=ingested["data"]["demoRunId"],
        alert_id=ingested["data"]["alertId"],
        staff_capability=staff_capability,
    )


def reset(client: Client, session: Session) -> Session:
    response, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/reset",
        headers={
            **command_headers(session),
            "Idempotency-Key": idempotency("reset"),
        },
    )
    return Session(
        session_id=session.session_id,
        run_id=response["data"]["demoRunId"],
        alert_id=response["data"]["alertId"],
        staff_capability=session.staff_capability,
    )


def normal(client: Client, session: Session) -> dict[str, Any]:
    response, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/alerts/{session.alert_id}/context",
        {"responseCode": "KNOWN_AND_INTENTIONAL", "demoBranchCode": "FIN_MGMT_A_NORMAL_CONTEXT"},
        {**command_headers(session), "Idempotency-Key": idempotency("normal")},
    )
    require(response["data"]["currentState"] == "CLOSED_NORMAL", "정상 시나리오가 종결되지 않았습니다.")
    queue, _ = client.request(
        "GET",
        f"/api/v1/demo/sessions/{session.session_id}/staff/cases",
        headers=command_headers(session, session.staff_capability),
    )
    require(len(queue["data"]["items"]) == 0, "정상 시나리오가 직원 사건을 생성했습니다.")
    return {"state": "CLOSED_NORMAL", "staffCaseCreated": False}


def escalate(client: Client, session: Session, prefix: str) -> tuple[str, int]:
    response, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/alerts/{session.alert_id}/context",
        {"responseCode": "UNABLE_TO_CONFIRM", "demoBranchCode": "FIN_MGMT_B_NO_CONTEXT"},
        {**command_headers(session), "Idempotency-Key": idempotency(prefix)},
    )
    require(response["data"]["currentState"] == "PENDING_BANK_REVIEW", "직원 검토 상태로 연결되지 않았습니다.")
    queue, _ = client.request(
        "GET",
        f"/api/v1/demo/sessions/{session.session_id}/staff/cases",
        headers=command_headers(session, session.staff_capability),
    )
    item = queue["data"]["items"][0]
    return item["caseId"], item["caseVersion"]


def caution(client: Client, session: Session) -> dict[str, Any]:
    case_id, version = escalate(client, session, "caution-context")
    headers = command_headers(session, session.staff_capability)
    draft, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/copilot-drafts",
        {"draftType": "CONSULTATION_NOTE"},
        headers,
    )
    generated = draft["data"]["draft"]
    require(generated["generatedBy"] == "RAG_GROUNDED_TEMPLATE", "승인 근거 RAG 초안이 생성되지 않았습니다.")
    require(len(generated["citations"]) >= 1, "주의 시나리오 citation이 없습니다.")
    reviewed, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/review",
        {"action": "START_REVIEW", "caseVersion": version, "note": "합성 근거와 고객 응답을 확인합니다.", "followUpAt": None},
        {**headers, "Idempotency-Key": idempotency("caution-review")},
    )
    guidance, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/guidance-plan",
        {"caseVersion": reviewed["data"]["caseVersion"], "decision": "APPROVE_GUIDANCE_PLAN", "selectedActionCodes": ["SAFE_BLOCK_INFO", "BANK_CONSULTATION"], "staffNote": "승인된 적용조건을 확인한 뒤 안내합니다."},
        {**headers, "Idempotency-Key": idempotency("caution-guidance")},
    )
    require(guidance["data"]["currentState"] == "GUIDANCE_PLAN_APPROVED", "주의 시나리오 안내계획이 승인되지 않았습니다.")
    require(not guidance["data"]["externalExecutionCreated"], "주의 시나리오가 외부 실행을 생성했습니다.")
    return {"state": "GUIDANCE_PLAN_APPROVED", "citationCount": len(generated["citations"]), "fallbackUsed": generated["fallbackUsed"], "externalExecutionCreated": False}


def false_positive(client: Client, session: Session) -> dict[str, Any]:
    case_id, version = escalate(client, session, "false-positive-context")
    headers = command_headers(session, session.staff_capability)
    reviewed, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/review",
        {"action": "START_REVIEW", "caseVersion": version, "note": "합성 근거와 처리 내역을 대조합니다.", "followUpAt": None},
        {**headers, "Idempotency-Key": idempotency("false-positive-review")},
    )
    closed, _ = client.request(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/review",
        {"action": "CLOSE_FALSE_POSITIVE", "caseVersion": reviewed["data"]["caseVersion"], "note": "처리 지연과 합성 이체 내역을 대조해 정상 활동임을 확인했습니다.", "followUpAt": None},
        {**headers, "Idempotency-Key": idempotency("false-positive-close")},
    )
    require(closed["data"]["currentState"] == "CLOSED_FALSE_POSITIVE", "오탐 시나리오가 종결되지 않았습니다.")
    require(not closed["data"]["externalExecutionCreated"], "오탐 시나리오가 외부 실행을 생성했습니다.")
    return {"state": "CLOSED_FALSE_POSITIVE", "humanReviewRequired": True, "externalExecutionCreated": False}


def main() -> int:
    parser = argparse.ArgumentParser(description="운영 Vercel→AWS 합성 시나리오 E2E")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    parsed = urlparse(args.base_url)
    allowed = parsed.scheme == "https" and parsed.hostname in {"alzs-well.vercel.app", "alzs-well-staff.vercel.app"}
    require(allowed, "운영 Vercel HTTPS 도메인만 사용할 수 있습니다.")

    client = Client(args.base_url)
    session = create_session(client)
    try:
        result = {"baseUrl": args.base_url, "syntheticDataOnly": True, "normal": normal(client, session)}
        session = reset(client, session)
        result["caution"] = caution(client, session)
        session = reset(client, session)
        result["falsePositive"] = false_positive(client, session)
        result["allPassed"] = True
        rendered = json.dumps(result, ensure_ascii=False, indent=2)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered + "\n", encoding="utf-8")
        print(rendered)
        return 0
    finally:
        try:
            client.request("DELETE", f"/api/v1/demo/sessions/{session.session_id}")
        except RuntimeError:
            pass


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (KeyError, IndexError, RuntimeError, urllib.error.URLError) as error:
        print(f"운영 시나리오 검증 실패: {error}", file=sys.stderr)
        raise SystemExit(1) from None
