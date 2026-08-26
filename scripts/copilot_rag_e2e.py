#!/usr/bin/env python3
"""격리 Compose에서 합성 ingestion부터 인용 코파일럿과 장애 폴백까지 검증한다."""

from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


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
    environment["AI_RETRIEVAL_ENABLED"] = "true"
    environment["COPILOT_RAG_ENABLED"] = "true"
    return environment


ENVIRONMENT = load_environment()
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


def run_demo_copilot() -> dict[str, Any]:
    created, response_headers = http("POST", "/api/v1/demo/sessions")
    session_id = created["data"]["sessionId"]
    customer_capability = response_headers.get("X-Demo-Customer-Capability")
    require(bool(customer_capability), "customer capability header missing")
    credentials = (
        ENVIRONMENT["DEMO_STAFF_USERNAME"] + ":" + ENVIRONMENT["DEMO_STAFF_PASSWORD"]
    ).encode("utf-8")
    _, staff_headers = http(
        "POST",
        f"/api/v1/demo/staff/sessions/{session_id}/capability",
        headers={"Authorization": "Basic " + base64.b64encode(credentials).decode("ascii")},
    )
    staff_capability = staff_headers.get("X-Demo-Staff-Capability")
    require(bool(staff_capability), "staff capability header missing")
    ingested, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session_id}/scenarios/FIN_MGMT_AB_001/ingest",
        headers={
            "X-Demo-Capability": customer_capability,
            "Idempotency-Key": SCENARIO_COMMAND_ID,
        },
    )
    run_id = ingested["data"]["demoRunId"]
    customer_headers = {
        "X-Demo-Capability": customer_capability,
        "X-Demo-Run-Id": run_id,
    }
    alerts, _ = http(
        "GET",
        f"/api/v1/demo/sessions/{session_id}/customers/SYN_CUSTOMER_FIN_MGMT_001/alerts",
        headers=customer_headers,
    )
    alert_id = alerts["data"]["items"][0]["alertId"]
    context_headers = dict(customer_headers)
    context_headers["Idempotency-Key"] = "copilot-rag-e2e-context-v1"
    http(
        "POST",
        f"/api/v1/demo/sessions/{session_id}/alerts/{alert_id}/context",
        {"responseCode": "UNABLE_TO_CONFIRM", "demoBranchCode": "FIN_MGMT_B_NO_CONTEXT"},
        context_headers,
    )
    staff_request_headers = {
        "X-Demo-Capability": staff_capability,
        "X-Demo-Run-Id": run_id,
    }
    cases, _ = http(
        "GET",
        f"/api/v1/demo/sessions/{session_id}/staff/cases",
        headers=staff_request_headers,
    )
    case_id = cases["data"]["items"][0]["caseId"]
    grounded, _ = http(
        "POST",
        f"/api/v1/demo/sessions/{session_id}/cases/{case_id}/copilot-drafts",
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
        f"/api/v1/demo/sessions/{session_id}/cases/{case_id}/copilot-drafts",
        {"draftType": "CONSULTATION_NOTE"},
        staff_request_headers,
    )
    fallback_draft = fallback["data"]["draft"]
    require(fallback_draft["generatedBy"] == "DETERMINISTIC_TEMPLATE", "fallback template missing")
    require(fallback_draft["fallbackUsed"], "fallback flag missing")
    require(not fallback_draft["citations"], "fallback must not expose citations")

    try:
        http(
            "DELETE",
            f"/api/v1/demo/sessions/{session_id}",
            headers={"X-Demo-Capability": customer_capability},
        )
    except RuntimeError:
        pass
    return {
        "documentId": DOCUMENT_ID,
        "ingestionMode": "POSTGRES",
        "retrievalMode": grounded_draft["retrievalMode"],
        "groundedTemplate": grounded_draft["generatedBy"],
        "citationCount": len(grounded_draft["citations"]),
        "fallbackTemplate": fallback_draft["generatedBy"],
        "modelInvoked": False,
        "externalEgressAttempted": False,
        "syntheticDataOnly": True,
    }


def main() -> int:
    ARTIFACT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    try:
        compose("config", "--quiet")
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
        ingestion_result = json.loads(ingestion.splitlines()[-1])
        require(ingestion_result["code"] == "HTML_INGESTION_COMPLETED", "AI ingestion failed")
        require(ingestion_result["storage"] == "POSTGRES", "AI PostgreSQL storage was not used")
        import_headers = bearer(access_token)
        import_headers["Idempotency-Key"] = "copilot-rag-e2e-import-v1"
        imported, _ = http(
            "POST",
            "/api/v1/admin/knowledge/ingestion-imports",
            ingestion_import_payload(),
            import_headers,
        )
        require(imported["code"] == "KNOWLEDGE_INGESTION_IMPORTED", "Spring import failed")
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
        evidence = run_demo_copilot()
        (ARTIFACT_DIRECTORY / "result.json").write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print("Copilot RAG E2E passed: ingestion, import, grounded citations, deterministic fallback")
        return 0
    finally:
        try:
            (ARTIFACT_DIRECTORY / "compose-ps.txt").write_text(
                compose("ps", "--all", capture=True) + "\n", encoding="utf-8"
            )
            (ARTIFACT_DIRECTORY / "compose.log").write_text(
                compose("logs", "--no-color", "--timestamps", capture=True) + "\n",
                encoding="utf-8",
            )
        except subprocess.CalledProcessError:
            pass
        subprocess.run(
            [*COMPOSE, "down", "--volumes", "--remove-orphans"],
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
