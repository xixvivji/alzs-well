from __future__ import annotations

import io
import json
import urllib.error
from pathlib import Path
from typing import Any

import pytest

from app.evaluation.load_test import (
    LoadThresholds,
    endpoint_metrics,
    evaluate_load_test,
    percentile,
    run_http_load,
    write_load_test_report,
)


class FakeResponse(io.BytesIO):
    def __enter__(self) -> FakeResponse:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()


def test_http_load_runs_warmup_and_concurrent_requests(monkeypatch: pytest.MonkeyPatch) -> None:
    calls: list[tuple[str, float | None, dict[str, Any]]] = []

    def urlopen(request: Any, timeout: float | None = None) -> FakeResponse:
        payload = json.loads(request.data)
        calls.append((request.full_url, timeout, payload))
        return FakeResponse(b'{"ok":true}')

    monkeypatch.setattr("app.evaluation.load_test.urllib.request.urlopen", urlopen)

    metrics = run_http_load(
        name="fastapi",
        url="http://127.0.0.1:18085/internal/v1/search",
        headers={"X-Internal-Service-Token": "synthetic-token"},
        payload_factory=lambda: {"requestId": "synthetic"},
        response_validator=lambda body: body == {"ok": True},
        request_count=6,
        concurrency=2,
        warmup_requests=2,
        timeout_seconds=1.5,
    )

    assert len(calls) == 8
    assert metrics.success_count == 6
    assert metrics.error_count == 0
    assert metrics.p50_ms is not None
    assert metrics.throughput_rps > 0


def test_http_load_rejects_failed_warmup_and_groups_safe_errors(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def invalid(request: Any, timeout: float | None = None) -> FakeResponse:
        del request, timeout
        return FakeResponse(b'{"ok":false}')

    monkeypatch.setattr("app.evaluation.load_test.urllib.request.urlopen", invalid)
    with pytest.raises(RuntimeError, match="RESPONSE_CONTRACT_INVALID"):
        run_http_load(
            name="fastapi",
            url="http://127.0.0.1/internal/v1/search",
            headers={},
            payload_factory=dict,
            response_validator=lambda body: body == {"ok": True},
            request_count=1,
            concurrency=1,
            warmup_requests=1,
        )

    def denied(request: Any, timeout: float | None = None) -> FakeResponse:
        del request, timeout
        raise urllib.error.HTTPError("safe", 401, "denied", {}, None)

    monkeypatch.setattr("app.evaluation.load_test.urllib.request.urlopen", denied)
    metrics = run_http_load(
        name="fastapi",
        url="http://127.0.0.1/internal/v1/search",
        headers={},
        payload_factory=dict,
        response_validator=lambda body: True,
        request_count=2,
        concurrency=1,
        warmup_requests=0,
    )
    assert metrics.errors == {"HTTP_401": 2}
    assert metrics.p95_ms is None


def test_metrics_and_gate_report_pass_and_write_atomically(tmp_path: Path) -> None:
    fastapi = endpoint_metrics(
        name="fastapi",
        request_count=4,
        concurrency=2,
        latencies_ms=[10.0, 20.0, 30.0, 40.0],
        errors={},
        duration_seconds=1.0,
    )
    spring = endpoint_metrics(
        name="spring",
        request_count=4,
        concurrency=2,
        latencies_ms=[20.0, 30.0, 40.0, 50.0],
        errors={},
        duration_seconds=1.0,
    )
    report = evaluate_load_test(
        model_id="dragonkue/snowflake-arctic-embed-l-v2.0-ko",
        model_version="snowflake-arctic-embed-l-v2.0-ko@test",
        dimensions=1024,
        startup_seconds=5.0,
        peak_rss_bytes=2_000_000_000,
        container_peak_memory_bytes=2_200_000_000,
        endpoints=(fastapi, spring),
    )
    json_path = tmp_path / "report.json"
    markdown_path = tmp_path / "report.md"

    write_load_test_report(report, json_path, markdown_path)

    assert report.passed
    assert json.loads(json_path.read_text(encoding="utf-8"))["passed"] is True
    markdown = markdown_path.read_text(encoding="utf-8")
    assert "| fastapi | 2 | 4 |" in markdown
    assert "including page cache" in markdown
    assert not (tmp_path / "report.json.tmp").exists()


def test_gate_reports_each_failure_without_sensitive_values() -> None:
    failed = endpoint_metrics(
        name="spring-api",
        request_count=2,
        concurrency=1,
        latencies_ms=[1_500.0],
        errors={"REQUEST_FAILED": 1},
        duration_seconds=2.0,
    )
    report = evaluate_load_test(
        model_id="approved/model",
        model_version="approved@test",
        dimensions=1024,
        startup_seconds=31.0,
        peak_rss_bytes=2_684_354_561,
        container_peak_memory_bytes=2_800_000_000,
        endpoints=(failed,),
        thresholds=LoadThresholds(),
    )

    assert not report.passed
    assert report.failures == (
        "STARTUP_TIME_EXCEEDED",
        "PEAK_RSS_EXCEEDED",
        "SPRING_API_ERROR_RATE_EXCEEDED",
        "SPRING_API_P95_EXCEEDED",
        "SPRING_API_THROUGHPUT_BELOW_MINIMUM",
    )


@pytest.mark.parametrize(
    ("values", "percentage", "expected"),
    [([], 95, None), ([1.0], 95, 1.0), ([1.0, 2.0, 3.0, 4.0], 50, 2.0)],
)
def test_percentile_uses_nearest_rank(
    values: list[float], percentage: int, expected: float | None
) -> None:
    assert percentile(values, percentage) == expected


def test_rejects_invalid_measurements_and_configuration() -> None:
    with pytest.raises(ValueError, match="configuration"):
        run_http_load(
            name="bad",
            url="http://127.0.0.1",
            headers={},
            payload_factory=dict,
            response_validator=lambda body: True,
            request_count=0,
            concurrency=1,
        )
    with pytest.raises(ValueError, match="counts"):
        endpoint_metrics(
            name="bad",
            request_count=2,
            concurrency=1,
            latencies_ms=[1.0],
            errors={},
            duration_seconds=1.0,
        )
    with pytest.raises(ValueError, match="percentage"):
        percentile([1.0], 101)
