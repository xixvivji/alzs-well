#!/usr/bin/env python3
"""Validate the AWS DB role names shared by Compose, examples, and role provisioning."""

from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AI_COMPOSE = ROOT / "backend" / "compose.aws-ai.yaml"
AI_ENV = ROOT / "backend" / ".env.aws-ai.example"
APP_COMPOSE = ROOT / "backend" / "compose.aws-app.yaml"
APP_ENV = ROOT / "backend" / ".env.aws-app.example"
ROLE_SCRIPT = ROOT / "backend" / "docker" / "create-database-roles.sh"
AI_SERVICE_ENV = ROOT / "ai-service" / ".env.example"
LOCAL_COMPOSE = ROOT / "backend" / "compose.yaml"
MIGRATION_ROOT = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
AWS_FOUNDATION = ROOT / "infra" / "aws-staging" / "foundation.yaml"
APP_GATEWAY = ROOT / "backend" / "docker" / "nginx.conf.template"

EXPECTED_INGESTION_ROLE = "alzswell_ai_ingestor"
EXPECTED_RUNTIME_ROLE = "alzswell_ai_runtime"
EXPECTED_EMBEDDING_BACKEND = "local-arctic-ko"
EXPECTED_EMBEDDING_DIMENSIONS = "1024"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def compose_default(source: str, variable: str) -> str | None:
    match = re.search(rf"\$\{{{re.escape(variable)}:-([^}}]+)}}", source)
    return match.group(1) if match else None


def env_value(source: str, variable: str) -> str | None:
    match = re.search(rf"^{re.escape(variable)}=([^\r\n]+)$", source, re.MULTILINE)
    return match.group(1) if match else None


def render_compose(compose: Path, env_file: Path, *, profile: str | None = None) -> dict:
    command = [
        "docker", "compose",
        "--project-directory", str(ROOT / "backend"),
        "--env-file", str(env_file),
        "-f", str(compose),
    ]
    if profile:
        command.extend(["--profile", profile])
    command.extend(["config", "--format", "json"])
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    return json.loads(completed.stdout)


def main() -> int:
    compose = read(AI_COMPOSE)
    aws_env = read(AI_ENV)
    role_script = read(ROLE_SCRIPT)
    ai_service_env = read(AI_SERVICE_ENV)
    aws_foundation = read(AWS_FOUNDATION)
    app_gateway = read(APP_GATEWAY)
    errors: list[str] = []

    contracts = {
        "AWS Compose ingestion default": (
            compose_default(compose, "RDS_AI_INGESTION_USER"), EXPECTED_INGESTION_ROLE,
        ),
        "AWS Compose runtime default": (
            compose_default(compose, "RDS_AI_RUNTIME_USER"), EXPECTED_RUNTIME_ROLE,
        ),
        "AWS example ingestion user": (
            env_value(aws_env, "RDS_AI_INGESTION_USER"), EXPECTED_INGESTION_ROLE,
        ),
        "AWS example runtime user": (
            env_value(aws_env, "RDS_AI_RUNTIME_USER"), EXPECTED_RUNTIME_ROLE,
        ),
        "AI service example user": (
            env_value(ai_service_env, "ALZS_AI_DB_USER"), EXPECTED_INGESTION_ROLE,
        ),
        "AWS example embedding backend": (
            env_value(read(APP_ENV), "AI_EXPECTED_EMBEDDING_BACKEND"), EXPECTED_EMBEDDING_BACKEND,
        ),
        "AWS example embedding dimensions": (
            env_value(read(APP_ENV), "AI_EXPECTED_EMBEDDING_DIMENSIONS"), EXPECTED_EMBEDDING_DIMENSIONS,
        ),
    }
    for label, (actual, expected) in contracts.items():
        if actual != expected:
            errors.append(f"{label}: expected {expected!r}, got {actual!r}")

    provisioned_roles = set(re.findall(r"create role ([a-z0-9_]+)", role_script, re.IGNORECASE))
    for role in (EXPECTED_INGESTION_ROLE, EXPECTED_RUNTIME_ROLE):
        if role not in provisioned_roles:
            errors.append(f"database role provisioning does not create {role!r}")

    legacy_role = "alzswell_ai_" + "ingestion"
    contract_paths = [AI_COMPOSE, AI_ENV, ROLE_SCRIPT, AI_SERVICE_ENV, LOCAL_COMPOSE]
    contract_paths.extend(sorted(MIGRATION_ROOT.glob("*.sql")))
    allowed_ai_roles = {EXPECTED_INGESTION_ROLE, EXPECTED_RUNTIME_ROLE}
    for path in contract_paths:
        source = read(path)
        if legacy_role in source:
            errors.append(f"legacy role {legacy_role!r} remains in {path.relative_to(ROOT)}")
        for role in set(re.findall(r"\balzswell_ai_[a-z0-9_]+\b", source)):
            if role not in allowed_ai_roles:
                errors.append(f"unknown AI database role {role!r} in {path.relative_to(ROOT)}")

    required_foundation_contracts = {
        "separate App runtime role": "  AppRuntimeRole:\n",
        "separate AI runtime role": "  AiRuntimeRole:\n",
        "separate App instance profile": "  AppRuntimeInstanceProfile:\n",
        "separate AI instance profile": "  AiRuntimeInstanceProfile:\n",
        "temporary DB bootstrap policy": "  DatabaseBootstrapPolicy:\n",
        "temporary AI ingestion policy": "  AiIngestionPolicy:\n",
        "temporary ECR image publish permission": '              - "ecr:PutImage"\n',
        "private AI service discovery": "  AiPrivateDnsRecord:\n",
        "separate App mTLS secret": "  AppTlsSecret:\n",
        "separate AI mTLS secret": "  AiTlsSecret:\n",
        "App CloudWatch log group": "  AppLogGroup:\n",
        "AI CloudWatch log group": "  AiLogGroup:\n",
        "tag-scoped staging operator role": "  StagingOperatorRole:\n",
        "App profile attachment": "IamInstanceProfile: !Ref AppRuntimeInstanceProfile",
        "AI profile attachment": "IamInstanceProfile: !Ref AiRuntimeInstanceProfile",
    }
    for label, marker in required_foundation_contracts.items():
        if marker not in aws_foundation:
            errors.append(f"AWS foundation is missing {label}: {marker.strip()!r}")

    forbidden_foundation_contracts = {
        "shared runtime role": r"^  RuntimeRole:$",
        "shared runtime instance profile": r"^  RuntimeInstanceProfile:$",
        "shared runtime profile attachment": r"IamInstanceProfile: !Ref RuntimeInstanceProfile",
    }
    for label, pattern in forbidden_foundation_contracts.items():
        if re.search(pattern, aws_foundation, re.MULTILINE):
            errors.append(f"AWS foundation still contains {label}")

    read_rate_contracts = {
        "network read rate": "zone=demo_read:10m rate=300r/m;",
        "capability read rate": "zone=demo_capability_read:10m rate=300r/m;",
        "network read burst": "limit_req zone=demo_read burst=80 nodelay;",
        "capability read burst": "limit_req zone=demo_capability_read burst=80 nodelay;",
    }
    for label, marker in read_rate_contracts.items():
        if marker not in app_gateway:
            errors.append(f"AWS app gateway is missing {label}: {marker!r}")
    for marker in (
        "zone=demo_session_create:10m rate=10r/m;",
        "zone=demo_mutation:10m rate=30r/m;",
        "zone=demo_capability_mutation:10m rate=30r/m;",
    ):
        if marker not in app_gateway:
            errors.append(f"AWS app gateway mutation/session limit changed unexpectedly: {marker!r}")

    bootstrap_policy = aws_foundation.split("  ImagePublishDeploymentPolicy:\n", 1)[-1].split("\n  AppInstance:\n", 1)[0]
    for repository in ("AppRepository.Arn", "AiRepository.Arn"):
        if repository not in bootstrap_policy:
            errors.append(f"temporary image publisher is missing {repository}")
    if "Condition: EnableImagePublishDeployment" not in bootstrap_policy:
        errors.append("temporary image publisher must have its own deployment condition")
    ingestion_policy = aws_foundation.split("  AiIngestionPolicy:\n", 1)[-1].split(
        "\n  ImagePublishDeploymentPolicy:\n", 1
    )[0]
    if "ecr:PutImage" in ingestion_policy:
        errors.append("AI ingestion policy must not publish ECR images")
    permanent_ai_policy = aws_foundation.split("  AiRuntimeRole:\n", 1)[-1].split(
        "\n  AiRuntimeInstanceProfile:\n", 1
    )[0]
    if "ecr:PutImage" in permanent_ai_policy:
        errors.append("permanent AI runtime role must not publish ECR images")
    operator_policy = aws_foundation.split("  StagingOperatorRole:\n", 1)[-1].split(
        "\n  DatabaseBootstrapPolicy:\n", 1
    )[0]
    for marker in ('"ssm:resourceTag/Project": alzs-well', '"ssm:resourceTag/Environment": staging'):
        if marker not in operator_policy:
            errors.append(f"staging operator command scope is missing {marker}")
    if "secretsmanager:GetSecretValue" in operator_policy:
        errors.append("staging operator role must not read deployment secrets directly")

    try:
        app_config = render_compose(APP_COMPOSE, APP_ENV)
        ai_config = render_compose(AI_COMPOSE, AI_ENV, profile="ingestion")
    except (subprocess.CalledProcessError, json.JSONDecodeError) as error:
        errors.append(f"AWS Compose rendering failed: {error}")
    else:
        rendered_roles = {
            "rendered AI runtime role": ai_config["services"]["ai-service"]["environment"].get("ALZS_AI_DB_USER"),
            "rendered AI ingestion role": ai_config["services"]["ingestion"]["environment"].get("ALZS_AI_DB_USER"),
        }
        for label, actual in rendered_roles.items():
            expected = EXPECTED_RUNTIME_ROLE if "runtime" in label else EXPECTED_INGESTION_ROLE
            if actual != expected:
                errors.append(f"{label}: expected {expected!r}, got {actual!r}")

        rendered_tmpfs = {
            "AWS app backend": app_config["services"]["backend"].get("tmpfs", []),
            "AWS AI runtime": ai_config["services"]["ai-service"].get("tmpfs", []),
            "AWS AI ingestion": ai_config["services"]["ingestion"].get("tmpfs", []),
        }
        for label, entries in rendered_tmpfs.items():
            if len(entries) != 1 or not entries[0].startswith("/tmp:size=") or ",mode=1777" not in entries[0]:
                errors.append(f"{label} tmpfs must render as one /tmp mount, got {entries!r}")
            if any(not entry.startswith("/") for entry in entries):
                errors.append(f"{label} contains an invalid tmpfs target: {entries!r}")

        backend_environment = app_config["services"]["backend"]["environment"]
        if backend_environment.get("AI_RETRIEVAL_BASE_URL") != "https://ai.internal:8443":
            errors.append("AWS app must call the private AI gateway over HTTPS on port 8443")
        if backend_environment.get("AI_TLS_KEY_STORE") != "/run/secrets/ai-client.p12":
            errors.append("AWS app must mount the AI mTLS client key store")
        if backend_environment.get("AI_TLS_TRUST_STORE") != "/run/secrets/ai-truststore.p12":
            errors.append("AWS app must mount the AI private trust store")
        if backend_environment.get("AI_EXPECTED_EMBEDDING_BACKEND") != EXPECTED_EMBEDDING_BACKEND:
            errors.append("AWS app must pin the approved embedding backend")
        if backend_environment.get("AI_EXPECTED_EMBEDDING_DIMENSIONS") != EXPECTED_EMBEDDING_DIMENSIONS:
            errors.append("AWS app must pin the approved embedding dimensions")

        expected_logging = {
            "AWS app gateway": (app_config["services"]["gateway"], "/alzs-well-staging/app", "gateway"),
            "AWS app backend": (app_config["services"]["backend"], "/alzs-well-staging/app", "backend"),
            "AWS AI gateway": (ai_config["services"]["ai-gateway"], "/alzs-well-staging/ai", "gateway"),
            "AWS AI runtime": (ai_config["services"]["ai-service"], "/alzs-well-staging/ai", "runtime"),
            "AWS AI ingestion": (ai_config["services"]["ingestion"], "/alzs-well-staging/ai", "ingestion"),
        }
        for label, (service, group, stream) in expected_logging.items():
            logging = service.get("logging", {})
            options = logging.get("options", {})
            if logging.get("driver") != "awslogs":
                errors.append(f"{label} must use the awslogs driver")
            if options.get("awslogs-group") != group or options.get("awslogs-stream") != stream:
                errors.append(f"{label} has an unexpected log destination: {options!r}")
            if options.get("awslogs-create-group") != "false":
                errors.append(f"{label} must not create log groups at runtime")

        ai_gateway = ai_config["services"].get("ai-gateway")
        if ai_gateway is None:
            errors.append("AWS AI Compose must include an mTLS gateway")
        else:
            gateway_ports = ai_gateway.get("ports", [])
            if len(gateway_ports) != 1 or gateway_ports[0].get("target") != 8443:
                errors.append(f"AI mTLS gateway must expose only 8443, got {gateway_ports!r}")
            gateway_targets = {
                volume.get("target")
                for volume in ai_gateway.get("volumes", [])
                if volume.get("type") == "bind"
            }
            required_targets = {
                "/run/secrets/ai-server.crt",
                "/run/secrets/ai-server.key",
                "/run/secrets/client-ca.crt",
            }
            if not required_targets.issubset(gateway_targets):
                errors.append("AI mTLS gateway is missing server or client CA material")

        ai_runtime = ai_config["services"]["ai-service"]
        if ai_runtime.get("ports"):
            errors.append("FastAPI port 8000 must not be published on the AI EC2 host")
        if ai_runtime.get("expose") != ["8000"]:
            errors.append("FastAPI must be exposed only to the internal mTLS gateway network")

    if errors:
        print("AWS deployment role contract invalid:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(
        "AWS deployment role contract valid: "
        f"runtime={EXPECTED_RUNTIME_ROLE}, ingestion={EXPECTED_INGESTION_ROLE}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
