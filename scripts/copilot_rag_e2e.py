#!/usr/bin/env python3
"""격리 Compose에서 합성 ingestion부터 인용 코파일럿과 장애 폴백까지 검증한다."""

from __future__ import annotations

import json
import os
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
ENV_FILE = Path(os.environ.get("COMPOSE_ENV_FILE", BACKEND / ".env.example"))
MANIFEST = "contracts/knowledge/fixtures/synthetic-copilot-grounding.yaml"
DOCUMENT_ID = "DOC-SYN-COPILOT-001"
VERSION = "1.0.0"
AS_OF = "2026-08-26"
QUERY = (
    "정기납부 미처리 고객 상담 안내 중복 송금 고객 상담 안내 "
    "거래 반복 확인 고객 상담 안내"
)
SCENARIO_COMMAND_ID = "copilot-rag-e2e-scenario-v1"
IDEMPOTENCY_HEADER = "Idempotency" + "-Key"


@dataclass(frozen=True)
class RehearsalSession:
    session_id: str
    customer_capability: str
    staff_capability: str
    run_id: str
    alert_id: str


def load_environment() -> dict[str, str]:
    environment = os.environ.copy()
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        environment.setdefault(key, value)
    environment.setdefault("COMPOSE_PROJECT_NAME", "alzs-well-copilot-rag-e2e")
    environment.setdefault("BACKEND_PORT", "18083")
    if environment.get("FRONTEND_PROXY_SHARED_SECRET", "") in {
        "",
        "replace-with-64-lowercase-hex",
    }:
        environment["FRONTEND_PROXY_SHARED_SECRET"] = secrets.token_hex(32)
    environment["AI_RETRIEVAL_ENABLED"] = "true"
    environment["AI_ASSISTANCE_ENABLED"] = "true"
    environment["COPILOT_RAG_ENABLED"] = "true"
    return environment


ENVIRONMENT = load_environment()
EMBEDDING_MODE = ENVIRONMENT.get("COPILOT_RAG_EMBEDDING_MODE", "hash")
LOAD_TEST_ENABLED = ENVIRONMENT.get("COPILOT_RAG_LOAD_TEST_ENABLED", "false").lower() == "true"
LOAD_TEST_PORT = int(ENVIRONMENT.get("AI_LOAD_TEST_PORT", "18085"))
BACKEND_LOAD_TEST_PORT = int(ENVIRONMENT.get("BACKEND_LOAD_TEST_PORT", "18086"))
ARTIFACT_DIRECTORY = Path(
    ENVIRONMENT.get("COPILOT_RAG_E2E_ARTIFACT_DIR", ROOT / "artifacts/copilot-rag-e2e")
)
COMPOSE = [
    "docker",
    "compose",
    "--project-directory",
    str(BACKEND),
    "--env-file",
    str(ENV_FILE),
    "--project-name",
    ENVIRONMENT["COMPOSE_PROJECT_NAME"],
    "-f",
    str(BACKEND / "compose.yaml"),
    "-f",
    str(BACKEND / "compose.integration.yaml"),
]
extra_compose_files = []
if extra_compose_file := ENVIRONMENT.get("COPILOT_RAG_EXTRA_COMPOSE_FILE"):
    extra_compose_files.append(extra_compose_file)
if configured_files := ENVIRONMENT.get("COPILOT_RAG_EXTRA_COMPOSE_FILES"):
    extra_compose_files.extend(value for value in configured_files.split(os.pathsep) if value)
if LOAD_TEST_ENABLED:
    extra_compose_files.append(str(BACKEND / "compose.load-test.yaml"))
resolved_extra_paths: set[Path] = set()
for extra_compose_file in extra_compose_files:
    extra_path = Path(extra_compose_file)
    if not extra_path.is_absolute():
        extra_path = ROOT / extra_path
    extra_path = extra_path.resolve()
    if extra_path in resolved_extra_paths:
        continue
    resolved_extra_paths.add(extra_path)
    COMPOSE.extend(["-f", str(extra_path)])
BASE_URL = f"http://127.0.0.1:{ENVIRONMENT['BACKEND_PORT']}"


def command(arguments: list[str], *, capture: bool = False) -> str:
    result = subprocess.run(
        arguments,
        cwd=ROOT,
        env=ENVIRONMENT,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    )
    return result.stdout.strip() if capture else ""


def compose(*arguments: str, capture: bool = False) -> str:
    return command([*COMPOSE, *arguments], capture=capture)


def psql(statement: str) -> str:
    return compose(
        "exec",
        "-T",
        "postgres",
        "psql",
        "-X",
        "-qAt",
        "-v",
        "ON_ERROR_STOP=1",
        "-U",
        ENVIRONMENT["POSTGRES_USER"],
        "-d",
        ENVIRONMENT["POSTGRES_DB"],
        "-c",
        statement,
        capture=True,
    )


def http(
    method: str,
    path: str,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[dict[str, Any], Any]:
    request_headers = dict(headers or {})
    payload = None
    if body is not None:
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    retryable = method == "GET" or "Idempotency-Key" in request_headers or path.endswith(
        "/copilot-drafts"
    )
    for attempt in range(1, 4):
        request = urllib.request.Request(
            BASE_URL + path,
            data=payload,
            headers=request_headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return json.load(response), response.headers
        except urllib.error.HTTPError as error:
            if error.code == 429 and retryable and attempt < 3:
                retry_after = error.headers.get("Retry-After", "2")
                time.sleep(max(1, min(5, int(retry_after or "2"))))
                continue
            try:
                code = json.load(error).get("code", "UNKNOWN")
            except (json.JSONDecodeError, AttributeError):
                code = "NON_JSON_RESPONSE"
            raise RuntimeError(f"HTTP {error.code} ({code}) for {method} {path}") from None
    raise RuntimeError(f"retry exhausted for {method} {path}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def bearer(token: str) -> dict[str, str]:
    return {"Authorization": "Bearer " + token}


def register_and_publish(access_token: str) -> None:
    headers = bearer(access_token)
    headers["Idempotency-Key"] = "copilot-rag-e2e-register-v1"
    registered, _ = http(
        "POST",
        "/api/v1/admin/knowledge/documents",
        {
            "documentId": DOCUMENT_ID,
            "versionLabel": VERSION,
            "title": "합성 금융생활 변화 상담 안내",
            "issuer": "ALZ's well 테스트",
            "sourceType": "SYNTHETIC_FIXTURE",
            "sourcePath": "contracts/knowledge/fixtures/synthetic-copilot-grounding.html",
            "sourceUrl": None,
            "sourceHash": "sha256:19f4ed930d266d28ab682eede71c28db99cdd5b1f0ec7efb5fe0bfc38f7927fb",
            "sourceTransformations": [],
            "documentType": "SYNTHETIC_FIXTURE",
            "classification": "INTERNAL",
            "audience": "STAFF",
            "allowedRoles": ["PROTECTION_STAFF", "DETECTION_ADMIN"],
            "effectiveFrom": "2026-08-21",
            "effectiveTo": None,
            "checkedAt": "2026-08-21",
            "usageRights": "SYNTHETIC_UNRESTRICTED",
            "supersedesDocumentId": None,
            "supersedesVersionLabel": None,
        },
        headers,
    )
    require(registered["code"] == "KNOWLEDGE_DOCUMENT_REGISTERED_FOR_REVIEW", "registration failed")
    publish_headers = bearer(access_token)
    publish_headers["Idempotency-Key"] = "copilot-rag-e2e-publish-v1"
    published, _ = http(
        "POST",
        f"/api/v1/admin/knowledge/documents/{DOCUMENT_ID}/publish",
        {
            "versionLabel": VERSION,
            "expectedVersion": 1,
            "approvalReference": "SYNTHETIC_E2E_APPROVAL_V1",
        },
        publish_headers,
    )
    require(published["code"] == "KNOWLEDGE_DOCUMENT_PUBLISHED", "publication failed")


def ingest_synthetic_document() -> dict[str, Any]:
    ingestion = compose(
        "--profile",
        "ai-tools",
        "run",
        "--build",
        "--rm",
        "--no-deps",
        "ai-ingestion",
        "ingest-html",
        "--repo-root",
        "/workspace",
        "--manifest",
        MANIFEST,
        "--as-of",
        AS_OF,
        "--storage",
        "postgres",
        capture=True,
    )
    result = json.loads(ingestion.splitlines()[-1])
    require(result["code"] == "HTML_INGESTION_COMPLETED", "AI ingestion failed")
    require(result["storage"] == "POSTGRES", "AI PostgreSQL storage was not used")
    return result


def verify_single_provider_snapshot_reingestion() -> None:
    if EMBEDDING_MODE == "arctic-ko":
        ingest_synthetic_document()
        stored = psql(
            f"""
            select coalesce(bool_and(
              e.embedding_model_id='dragonkue/snowflake-arctic-embed-l-v2.0-ko'
              and e.embedding_model_version=
                'snowflake-arctic-embed-l-v2.0-ko@55ec6e9358a56d56af759bc8372e970caf8c305f'
              and e.embedding_dimensions=1024
            ), false)
            from ai_knowledge.chunk c
            join ai_knowledge.chunk_embedding e on e.chunk_id=c.chunk_id
            where c.document_id='{DOCUMENT_ID}' and c.version_label='{VERSION}';
            """
        )
        require(stored == "t", "Arctic-ko 1024 snapshot was not replaced consistently")
        return
    psql(
        f"""
        insert into ai_knowledge.chunk_embedding(
          chunk_id, embedding_model_id, embedding_model_version,
          embedding_dimensions, embedding, created_at
        )
        select chunk_id,
          'dragonkue/snowflake-arctic-embed-l-v2.0-ko',
          'snowflake-arctic-embed-l-v2.0-ko@55ec6e9358a56d56af759bc8372e970caf8c305f',
          1024,
          (array[1.0::real] || array_fill(0.0::real, array[1023]))::vector,
          now()
        from ai_knowledge.chunk
        where document_id='{DOCUMENT_ID}' and version_label='{VERSION}';
        """
    )
    ingest_synthetic_document()
    replacement = psql(
        f"""
        select coalesce(bool_and(
          model_count = 1
          and model_ids = array['local-hash-ngram-ko']::text[]
          and model_versions = array['local-hash-ngram-ko-v1']::text[]
          and dimensions = array[384]::bigint[]
        ), false)
        from (
          select c.chunk_id, count(*) as model_count,
            array_agg(e.embedding_model_id order by e.embedding_model_id)::text[]
              as model_ids,
            array_agg(e.embedding_model_version order by e.embedding_model_version)
              ::text[] as model_versions,
            array_agg(e.embedding_dimensions::bigint order by e.embedding_dimensions)
              as dimensions
          from ai_knowledge.chunk c
          join ai_knowledge.chunk_embedding e on e.chunk_id=c.chunk_id
          where c.document_id='{DOCUMENT_ID}' and c.version_label='{VERSION}'
          group by c.chunk_id
        ) stored;
        """
    )
    require(replacement == "t", "re-ingestion did not keep one current-provider snapshot")


def ingestion_import_payload() -> dict[str, Any]:
    statement = f"""
        with selected_run as (
          select * from ai_knowledge.ingestion_run
           where document_id='{DOCUMENT_ID}' and version_label='{VERSION}' and status='SUCCEEDED'
           order by started_at desc limit 1
        )
        select jsonb_build_object(
          'contractVersion','1.0.0',
          'ingestionRunId',r.run_id,
          'documentId',r.document_id,
          'versionLabel',r.version_label,
          'sourceHash',r.source_hash,
          'asOf',r.as_of,
          'extractorVersion',r.extractor_version,
          'chunkerVersion',r.chunker_version,
          'chunks',jsonb_agg(jsonb_build_object(
            'chunkId',c.chunk_id,
            'chunkOrder',c.chunk_order,
            'heading',c.heading,
            'sectionPath',to_jsonb(c.section_path),
            'page',c.page,
            'pageStart',c.page_start,
            'pageEnd',c.page_end,
            'text',c.content,
            'textHash',c.text_hash,
            'sourceHash',c.source_hash,
            'extractorVersion',c.extractor_version,
            'chunkerVersion',c.chunker_version
          ) order by c.chunk_order)
        )::text
        from selected_run r join ai_knowledge.chunk c on c.run_id=r.run_id
        group by r.run_id,r.document_id,r.version_label,r.source_hash,r.as_of,
                 r.extractor_version,r.chunker_version;
    """
    raw = psql(statement)
    require(bool(raw), "AI ingestion import payload was not created")
    return json.loads(raw)


def create_rehearsal_session() -> RehearsalSession:
    created, response_headers = http("POST", "/api/v1/demo/sessions")
    session_id = created["data"]["sessionId"]
    customer_capability = response_headers.get("X-Demo-Customer-Capability")
    require(bool(customer_capability), "customer capability header missing")
    _, staff_headers = http(
        "POST",
        f"/api/v1/demo/staff/sessions/{session_id}/capability",
        headers={"Authorization": "Bearer " + ENVIRONMENT["DEMO_STAFF_BOOTSTRAP_TOKEN"]},
    )
    staff_capability = staff_headers.get("X-Demo-Staff-Capability")
    require(bool(staff_capability), "staff capability header missing")
    ingested, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session_id}/scenarios/FIN_MGMT_AB_001/ingest",
        headers={
            "X-Demo-Capability": customer_capability,
            IDEMPOTENCY_HEADER: SCENARIO_COMMAND_ID,
        },
    )
    change_analysis, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session_id}/customers/{ingested['data']['customerId']}"
        "/ai-financial-assistance/change-analysis",
        headers={
            "X-Demo-Capability": customer_capability,
            "X-Demo-Run-Id": ingested["data"]["demoRunId"],
        },
    )
    require(
        change_analysis["data"]["fallbackUsed"] is False,
        "FastAPI change analysis was rejected by the Spring verifier",
    )
    require(
        any(item["changeDetected"] for item in change_analysis["data"]["changes"]),
        "change-analysis interop fixture did not exercise a detected change",
    )
    return RehearsalSession(
        session_id=session_id,
        customer_capability=customer_capability,
        staff_capability=staff_capability,
        run_id=ingested["data"]["demoRunId"],
        alert_id=ingested["data"]["alertId"],
    )


def reset_rehearsal_session(session: RehearsalSession, idempotency_key: str) -> RehearsalSession:
    reset, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/reset",
        headers={
            "X-Demo-Capability": session.customer_capability,
            "X-Demo-Run-Id": session.run_id,
            IDEMPOTENCY_HEADER: idempotency_key,
        },
    )
    require(reset["data"]["previousDemoRunId"] == session.run_id, "previous run mismatch")
    return RehearsalSession(
        session_id=session.session_id,
        customer_capability=session.customer_capability,
        staff_capability=session.staff_capability,
        run_id=reset["data"]["demoRunId"],
        alert_id=reset["data"]["alertId"],
    )


def run_normal_scenario(session: RehearsalSession) -> dict[str, Any]:
    customer_headers = {
        "X-Demo-Capability": session.customer_capability,
        "X-Demo-Run-Id": session.run_id,
        IDEMPOTENCY_HEADER: "rehearsal-normal-context-v1",
    }
    applied, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/alerts/{session.alert_id}/context",
        {"responseCode": "KNOWN_AND_INTENTIONAL", "demoBranchCode": "FIN_MGMT_A_NORMAL_CONTEXT"},
        customer_headers,
    )
    require(applied["data"]["currentState"] == "CLOSED_NORMAL", "normal scenario did not close")
    require(
        applied["data"]["t1ContextEvidence"]["structuralEvidenceMatched"],
        "normal scenario structural evidence did not match",
    )
    case_count = psql(
        "select count(*) from protection_case "
        f"where demo_session_id='{session.session_id}'::uuid "
        f"and demo_run_id='{session.run_id}'::uuid;"
    )
    require(case_count == "0", "normal scenario created a staff case")
    return {
        "currentState": "CLOSED_NORMAL",
        "structuralEvidenceMatched": True,
        "staffCaseCreated": False,
    }


def run_demo_copilot(session: RehearsalSession) -> dict[str, Any]:
    customer_headers = {
        "X-Demo-Capability": session.customer_capability,
        "X-Demo-Run-Id": session.run_id,
    }
    alerts, _ = http(
        "GET",
        f"/api/v1/demo/sessions/{session.session_id}/customers/SYN_CUSTOMER_FIN_MGMT_001/alerts",
        headers=customer_headers,
    )
    alert_id = alerts["data"]["items"][0]["alertId"]
    context_headers = dict(customer_headers)
    context_headers["Idempotency-Key"] = "copilot-rag-e2e-context-v1"
    http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/alerts/{alert_id}/context",
        {"responseCode": "UNABLE_TO_CONFIRM", "demoBranchCode": "FIN_MGMT_B_NO_CONTEXT"},
        context_headers,
    )
    staff_request_headers = {
        "X-Demo-Capability": session.staff_capability,
        "X-Demo-Run-Id": session.run_id,
    }
    cases, _ = http(
        "GET",
        f"/api/v1/demo/sessions/{session.session_id}/staff/cases",
        headers=staff_request_headers,
    )
    case_id = cases["data"]["items"][0]["caseId"]
    grounded, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/copilot-drafts",
        {"draftType": "CONSULTATION_NOTE"},
        staff_request_headers,
    )
    grounded_draft = grounded["data"]["draft"]
    require(grounded_draft["generatedBy"] == "RAG_GROUNDED_TEMPLATE", "grounded template missing")
    require(grounded_draft["retrievalMode"] == "INTERNAL_RAG_HYBRID", "hybrid mode missing")
    require(len(grounded_draft["citations"]) > 0, "grounded citations missing")
    require(
        all(item["documentId"] == DOCUMENT_ID for item in grounded_draft["citations"]),
        "unexpected citation document",
    )
    require(not grounded_draft["modelInvoked"], "external model was invoked")
    require(not grounded_draft["externalEgressAttempted"], "external egress was attempted")

    compose("--profile", "ai", "stop", "ai-service")
    fallback, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/copilot-drafts",
        {"draftType": "CONSULTATION_NOTE"},
        staff_request_headers,
    )
    fallback_draft = fallback["data"]["draft"]
    require(fallback_draft["generatedBy"] == "DETERMINISTIC_TEMPLATE", "fallback template missing")
    require(fallback_draft["fallbackUsed"], "fallback flag missing")
    require(not fallback_draft["citations"], "fallback must not expose citations")

    review_headers = dict(staff_request_headers)
    review_headers[IDEMPOTENCY_HEADER] = "rehearsal-caution-review-v1"
    reviewed, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/review",
        {
            "action": "START_REVIEW",
            "caseVersion": 1,
            "note": "고객 응답과 합성 근거를 확인합니다.",
            "followUpAt": None,
        },
        review_headers,
    )
    require(reviewed["data"]["currentState"] == "IN_BANK_REVIEW", "caution review did not start")
    guidance_headers = dict(staff_request_headers)
    guidance_headers[IDEMPOTENCY_HEADER] = "rehearsal-caution-guidance-v1"
    guidance, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/guidance-plan",
        {
            "caseVersion": 2,
            "decision": "APPROVE_GUIDANCE_PLAN",
            "selectedActionCodes": ["SAFE_BLOCK_INFO", "BANK_CONSULTATION"],
            "staffNote": "승인된 공식 적용조건을 확인한 뒤 고객에게 안내합니다.",
        },
        guidance_headers,
    )
    require(
        guidance["data"]["currentState"] == "GUIDANCE_PLAN_APPROVED",
        "caution guidance plan was not approved",
    )
    require(not guidance["data"]["externalExecutionCreated"], "caution scenario created external execution")

    return {
        "documentId": DOCUMENT_ID,
        "ingestionMode": "POSTGRES",
        "retrievalMode": grounded_draft["retrievalMode"],
        "groundedTemplate": grounded_draft["generatedBy"],
        "citationCount": len(grounded_draft["citations"]),
        "fallbackTemplate": fallback_draft["generatedBy"],
        "currentState": guidance["data"]["currentState"],
        "modelInvoked": False,
        "externalEgressAttempted": False,
        "syntheticDataOnly": True,
    }


def run_false_positive_scenario(session: RehearsalSession) -> dict[str, Any]:
    customer_headers = {
        "X-Demo-Capability": session.customer_capability,
        "X-Demo-Run-Id": session.run_id,
        IDEMPOTENCY_HEADER: "rehearsal-false-positive-context-v1",
    }
    escalated, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/alerts/{session.alert_id}/context",
        {"responseCode": "UNABLE_TO_CONFIRM", "demoBranchCode": "FIN_MGMT_B_NO_CONTEXT"},
        customer_headers,
    )
    require(
        escalated["data"]["currentState"] == "PENDING_BANK_REVIEW",
        "false-positive scenario was not escalated before human review",
    )
    staff_request_headers = {
        "X-Demo-Capability": session.staff_capability,
        "X-Demo-Run-Id": session.run_id,
    }
    cases, _ = http(
        "GET",
        f"/api/v1/demo/sessions/{session.session_id}/staff/cases",
        headers=staff_request_headers,
    )
    case_id = cases["data"]["items"][0]["caseId"]
    detail, _ = http(
        "GET",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}",
        headers=staff_request_headers,
    )
    close_action = next(
        item for item in detail["data"]["allowedActions"]
        if item["action"] == "CLOSE_FALSE_POSITIVE"
    )
    require(not close_action["enabled"], "false-positive close was enabled before review")
    review_headers = dict(staff_request_headers)
    review_headers[IDEMPOTENCY_HEADER] = "rehearsal-false-positive-review-v1"
    reviewed, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/review",
        {
            "action": "START_REVIEW",
            "caseVersion": 1,
            "note": "고객 응답과 합성 근거를 확인합니다.",
            "followUpAt": None,
        },
        review_headers,
    )
    require(reviewed["data"]["currentState"] == "IN_BANK_REVIEW", "false-positive review did not start")
    close_headers = dict(staff_request_headers)
    close_headers[IDEMPOTENCY_HEADER] = "rehearsal-false-positive-close-v1"
    closed, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session.session_id}/cases/{case_id}/review",
        {
            "action": "CLOSE_FALSE_POSITIVE",
            "caseVersion": 2,
            "note": "거래 처리 지연과 실제 이체 내역을 대조해 정상 활동임을 확인했습니다.",
            "followUpAt": None,
        },
        close_headers,
    )
    require(
        closed["data"]["currentState"] == "CLOSED_FALSE_POSITIVE",
        "false-positive scenario did not close",
    )
    require(not closed["data"]["externalExecutionCreated"], "false-positive scenario created external execution")
    return {
        "currentState": "CLOSED_FALSE_POSITIVE",
        "humanReviewRequired": True,
        "externalExecutionCreated": False,
    }


def ai_service_startup_seconds() -> float:
    container_id = compose("ps", "-q", "ai-service", capture=True)
    require(bool(container_id), "AI service container was not found")
    inspection = json.loads(command(["docker", "inspect", container_id], capture=True))[0]
    started_at = _timestamp(inspection["State"]["StartedAt"])
    successful = [
        _timestamp(item["End"])
        for item in inspection["State"].get("Health", {}).get("Log", [])
        if item.get("ExitCode") == 0
    ]
    require(bool(successful), "AI service successful health check was not found")
    return max(0.0, (min(successful) - started_at).total_seconds())


def ai_service_memory_measurements() -> tuple[int, int]:
    peak_rss_raw = compose(
        "exec",
        "-T",
        "ai-service",
        "python",
        "-c",
        (
            "from pathlib import Path; "
            "lines=Path('/proc/1/status').read_text().splitlines(); "
            "value=next(line for line in lines if line.startswith('VmHWM:')); "
            "print(int(value.split()[1])*1024)"
        ),
        capture=True,
    )
    container_peak_raw = compose(
        "exec",
        "-T",
        "ai-service",
        "python",
        "-c",
        "from pathlib import Path; print(Path('/sys/fs/cgroup/memory.peak').read_text().strip())",
        capture=True,
    )
    peak_rss = int(peak_rss_raw)
    container_peak = int(container_peak_raw)
    require(peak_rss > 0, "AI service peak RSS was not available")
    require(container_peak >= peak_rss, "AI service container peak memory was invalid")
    return peak_rss, container_peak


def warm_arctic_search() -> float:
    require(EMBEDDING_MODE == "arctic-ko", "warmup requires Arctic-ko mode")
    require(LOAD_TEST_ENABLED, "warmup requires the isolated load-test port")
    sys.path.insert(0, str(ROOT / "ai-service"))
    from app.evaluation.load_test import run_http_load  # pylint: disable=import-outside-toplevel

    metrics = run_http_load(
        name="fastapi-cold-start",
        url=f"http://127.0.0.1:{LOAD_TEST_PORT}/internal/v1/search",
        headers={"X-Internal-Service-Token": ENVIRONMENT["AI_INTERNAL_TOKEN"]},
        payload_factory=lambda: {
            "contractVersion": "1.0.0",
            "requestId": str(uuid4()),
            "query": QUERY,
            "permissions": ["KNOWLEDGE_SEARCH"],
            "principalRoles": ["DETECTION_ADMIN"],
            "requesterAudiences": ["STAFF"],
            "asOf": AS_OF,
            "limit": 5,
        },
        response_validator=lambda body: (
            body.get("contractVersion") == "1.0.0"
            and bool(body.get("results"))
            and all(
                item.get("citation", {}).get("indexVersion") == "hybrid-arctic-ko-v1"
                for item in body["results"]
            )
        ),
        request_count=1,
        concurrency=1,
        warmup_requests=0,
        timeout_seconds=90.0,
    )
    require(metrics.success_count == 1, "Arctic-ko cold-start warmup failed")
    require(metrics.p95_ms is not None, "Arctic-ko cold-start latency was not measured")
    return metrics.p95_ms


def run_arctic_load_test(access_token: str, startup_seconds: float) -> dict[str, Any]:
    require(EMBEDDING_MODE == "arctic-ko", "load test requires Arctic-ko mode")
    sys.path.insert(0, str(ROOT / "ai-service"))
    from app.evaluation.load_test import (  # pylint: disable=import-outside-toplevel
        evaluate_load_test,
        run_http_load,
        write_load_test_report,
    )

    request_count = int(ENVIRONMENT.get("AI_LOAD_TEST_REQUEST_COUNT", "100"))
    concurrency = int(ENVIRONMENT.get("AI_LOAD_TEST_CONCURRENCY", "4"))
    def direct_payload() -> dict[str, Any]:
        return {
            "contractVersion": "1.0.0",
            "requestId": str(uuid4()),
            "query": QUERY,
            "permissions": ["KNOWLEDGE_SEARCH"],
            "principalRoles": ["DETECTION_ADMIN"],
            "requesterAudiences": ["STAFF"],
            "asOf": AS_OF,
            "limit": 5,
        }

    direct = run_http_load(
        name="fastapi",
        url=f"http://127.0.0.1:{LOAD_TEST_PORT}/internal/v1/search",
        headers={"X-Internal-Service-Token": ENVIRONMENT["AI_INTERNAL_TOKEN"]},
        payload_factory=direct_payload,
        response_validator=lambda body: (
            body.get("contractVersion") == "1.0.0"
            and bool(body.get("results"))
            and all(
                item.get("citation", {}).get("indexVersion") == "hybrid-arctic-ko-v1"
                for item in body["results"]
            )
        ),
        request_count=request_count,
        concurrency=concurrency,
        warmup_requests=max(8, concurrency * 2),
    )

    def spring_payload() -> dict[str, Any]:
        return {"query": QUERY, "asOf": AS_OF, "audience": "STAFF", "limit": 5}

    spring = run_http_load(
        name="spring",
        url=f"http://127.0.0.1:{BACKEND_LOAD_TEST_PORT}/api/v1/knowledge/search",
        headers=bearer(access_token),
        payload_factory=spring_payload,
        response_validator=lambda body: (
            body.get("code") == "KNOWLEDGE_SEARCH_COMPLETED"
            and body.get("data", {}).get("vectorSearchUsed") is True
            and body.get("data", {}).get("total", 0) > 0
        ),
        request_count=request_count,
        concurrency=concurrency,
        warmup_requests=max(8, concurrency * 2),
    )
    peak_rss_bytes, container_peak_memory_bytes = ai_service_memory_measurements()
    report = evaluate_load_test(
        model_id="dragonkue/snowflake-arctic-embed-l-v2.0-ko",
        model_version=(
            "snowflake-arctic-embed-l-v2.0-ko@"
            "55ec6e9358a56d56af759bc8372e970caf8c305f"
        ),
        dimensions=1024,
        startup_seconds=startup_seconds,
        peak_rss_bytes=peak_rss_bytes,
        container_peak_memory_bytes=container_peak_memory_bytes,
        endpoints=(direct, spring),
    )
    write_load_test_report(
        report,
        ARTIFACT_DIRECTORY / "load-test.json",
        ARTIFACT_DIRECTORY / "load-test.md",
    )
    require(report.passed, "Arctic-ko load gate failed: " + ",".join(report.failures))
    return {
        "passed": report.passed,
        "startupSeconds": report.startup_seconds,
        "peakRssBytes": report.peak_rss_bytes,
        "containerPeakMemoryBytes": report.container_peak_memory_bytes,
        "fastApiP95Ms": direct.p95_ms,
        "springP95Ms": spring.p95_ms,
        "concurrency": concurrency,
        "requestCountPerEndpoint": request_count,
    }


def _timestamp(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def main() -> int:
    ARTIFACT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    try:
        compose("config", "--quiet")
        compose(
            "--profile",
            "ai",
            "--profile",
            "ai-tools",
            "down",
            "--volumes",
            "--remove-orphans",
        )
        compose(
            "--profile",
            "ai",
            "up",
            "--build",
            "--detach",
            "--wait",
            "--wait-timeout",
            "300",
        )
        if LOAD_TEST_ENABLED:
            published_port = compose(
                "--profile", "ai", "port", "ai-service", "8000", capture=True
            )
            require(
                published_port.endswith(f":{LOAD_TEST_PORT}"),
                "AI load test port was not published",
            )
            backend_port = compose("port", "backend", "8080", capture=True)
            require(
                backend_port.endswith(f":{BACKEND_LOAD_TEST_PORT}"),
                "backend load test port was not published",
            )
        startup_seconds = ai_service_startup_seconds() if LOAD_TEST_ENABLED else 0.0
        readiness, _ = http("GET", "/api/v1/system/readiness")
        require(readiness["data"]["ready"], "backend readiness failed")
        psql(
            "insert into auth_principal_role(principal_id,role_code) "
            "select principal_id,'DETECTION_ADMIN' from auth_principal "
            "where login_id='synthetic-customer' on conflict do nothing;"
        )
        login, _ = http(
            "POST",
            "/api/v1/auth/login",
            {
                "loginId": "synthetic-customer",
                "password": "local-synthetic-customer-password",
            },
        )
        access_token = login["data"]["accessToken"]
        register_and_publish(access_token)
        ingest_synthetic_document()
        verify_single_provider_snapshot_reingestion()
        import_headers = bearer(access_token)
        import_headers["Idempotency-Key"] = "copilot-rag-e2e-import-v1"
        imported, _ = http(
            "POST",
            "/api/v1/admin/knowledge/ingestion-imports",
            ingestion_import_payload(),
            import_headers,
        )
        require(imported["code"] == "KNOWLEDGE_INGESTION_IMPORTED", "Spring import failed")
        cold_start_warmup_ms = (
            warm_arctic_search()
            if LOAD_TEST_ENABLED and EMBEDDING_MODE == "arctic-ko"
            else None
        )
        searched, _ = http(
            "POST",
            "/api/v1/knowledge/search",
            {"query": QUERY, "asOf": AS_OF, "audience": "STAFF", "limit": 5},
            bearer(access_token),
        )
        require(searched["data"]["vectorSearchUsed"], "FastAPI hybrid search was not used")
        require(searched["data"]["total"] > 0, "FastAPI did not return the synthetic evidence")
        require(
            searched["data"]["items"][0]["passage"]["documentId"] == DOCUMENT_ID,
            "Spring citation validation rejected the synthetic evidence",
        )
        index_version = psql(
            "select index_version from ai_knowledge.retrieval_run "
            "where status='SUCCEEDED' order by started_at desc limit 1;"
        )
        if EMBEDDING_MODE == "arctic-ko":
            require(index_version == "hybrid-arctic-ko-v1", "Arctic-ko index version missing")
        load_test = (
            run_arctic_load_test(access_token, startup_seconds)
            if LOAD_TEST_ENABLED
            else None
        )
        rehearsal_session = create_rehearsal_session()
        try:
            normal_scenario = run_normal_scenario(rehearsal_session)
            rehearsal_session = reset_rehearsal_session(
                rehearsal_session,
                "rehearsal-normal-to-caution-reset-v1",
            )
            evidence = run_demo_copilot(rehearsal_session)
            rehearsal_session = reset_rehearsal_session(
                rehearsal_session,
                "rehearsal-caution-to-false-positive-reset-v1",
            )
            false_positive_scenario = run_false_positive_scenario(rehearsal_session)
        finally:
            try:
                http(
                    "DELETE",
                    f"/api/v1/demo/sessions/{rehearsal_session.session_id}",
                    headers={"X-Demo-Capability": rehearsal_session.customer_capability},
                )
            except RuntimeError:
                pass
        evidence["scenarios"] = {
            "normal": normal_scenario,
            "caution": {"currentState": evidence["currentState"]},
            "falsePositive": false_positive_scenario,
        }
        evidence["embeddingMode"] = EMBEDDING_MODE
        evidence["indexVersion"] = index_version
        if cold_start_warmup_ms is not None:
            evidence["coldStartWarmupMs"] = cold_start_warmup_ms
        if load_test is not None:
            evidence["loadTest"] = load_test
        (ARTIFACT_DIRECTORY / "result.json").write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(
            "Demo rehearsal E2E passed: normal, caution, false-positive, "
            "grounded citations, deterministic fallback"
        )
        return 0
    finally:
        try:
            (ARTIFACT_DIRECTORY / "compose-ps.txt").write_text(
                compose(
                    "--profile",
                    "ai",
                    "--profile",
                    "ai-tools",
                    "ps",
                    "--all",
                    capture=True,
                )
                + "\n",
                encoding="utf-8",
            )
            (ARTIFACT_DIRECTORY / "compose.log").write_text(
                compose("logs", "--no-color", "--timestamps", capture=True) + "\n",
                encoding="utf-8",
            )
        except subprocess.CalledProcessError:
            pass
        subprocess.run(
            [
                *COMPOSE,
                "--profile",
                "ai",
                "--profile",
                "ai-tools",
                "down",
                "--volumes",
                "--remove-orphans",
            ],
            cwd=ROOT,
            env=ENVIRONMENT,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (KeyError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Copilot RAG E2E failed: {error}", file=sys.stderr)
        raise SystemExit(1) from None
