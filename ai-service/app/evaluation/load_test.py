from __future__ import annotations

import json
import math
import time
import urllib.error
import urllib.request
from collections import Counter
from collections.abc import Callable, Mapping
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


PayloadFactory = Callable[[], dict[str, Any]]
ResponseValidator = Callable[[dict[str, Any]], bool]


@dataclass(frozen=True, slots=True)
class LoadThresholds:
    max_p95_ms: float = 1_000.0
    max_error_rate: float = 0.0
    min_throughput_rps: float = 2.0
    max_peak_rss_bytes: int = 2_684_354_560
    max_startup_seconds: float = 30.0


@dataclass(frozen=True, slots=True)
class EndpointMetrics:
    name: str
    request_count: int
    concurrency: int
    success_count: int
    error_count: int
    error_rate: float
    duration_seconds: float
    throughput_rps: float
    p50_ms: float | None
    p95_ms: float | None
    p99_ms: float | None
    errors: dict[str, int]


@dataclass(frozen=True, slots=True)
class LoadTestReport:
    contract_version: str
    model_id: str
    model_version: str
    dimensions: int
    startup_seconds: float
    peak_rss_bytes: int
    container_peak_memory_bytes: int
    thresholds: LoadThresholds
    endpoints: tuple[EndpointMetrics, ...]
    failures: tuple[str, ...]

    @property
    def passed(self) -> bool:
        return not self.failures


def run_http_load(
    *,
    name: str,
    url: str,
    headers: Mapping[str, str],
    payload_factory: PayloadFactory,
    response_validator: ResponseValidator,
    request_count: int,
    concurrency: int,
    warmup_requests: int = 3,
    timeout_seconds: float = 3.0,
) -> EndpointMetrics:
    if request_count < 1 or concurrency < 1 or warmup_requests < 0 or timeout_seconds <= 0:
        raise ValueError("invalid load test configuration")
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        warmups = [
            executor.submit(
                _request_once,
                url,
                headers,
                payload_factory(),
                response_validator,
                timeout_seconds,
            )
            for _ in range(warmup_requests)
        ]
        for future in as_completed(warmups):
            _, error = future.result()
            if error is not None:
                raise RuntimeError("load test warmup failed: " + error)

    latencies: list[float] = []
    errors: Counter[str] = Counter()
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(
                _request_once,
                url,
                headers,
                payload_factory(),
                response_validator,
                timeout_seconds,
            )
            for _ in range(request_count)
        ]
        for future in as_completed(futures):
            latency, error = future.result()
            if error is None:
                latencies.append(latency)
            else:
                errors[error] += 1
    duration = max(time.perf_counter() - started, 1e-9)
    return endpoint_metrics(
        name=name,
        request_count=request_count,
        concurrency=concurrency,
        latencies_ms=latencies,
        errors=dict(sorted(errors.items())),
        duration_seconds=duration,
    )


def endpoint_metrics(
    *,
    name: str,
    request_count: int,
    concurrency: int,
    latencies_ms: list[float],
    errors: dict[str, int],
    duration_seconds: float,
) -> EndpointMetrics:
    if request_count < 1 or concurrency < 1 or duration_seconds <= 0:
        raise ValueError("invalid load test measurements")
    if any(value < 0 or not math.isfinite(value) for value in latencies_ms):
        raise ValueError("invalid latency measurement")
    error_count = sum(errors.values())
    if error_count < 0 or len(latencies_ms) + error_count != request_count:
        raise ValueError("load test counts do not match")
    ordered = sorted(latencies_ms)
    return EndpointMetrics(
        name=name,
        request_count=request_count,
        concurrency=concurrency,
        success_count=len(ordered),
        error_count=error_count,
        error_rate=error_count / request_count,
        duration_seconds=duration_seconds,
        throughput_rps=request_count / duration_seconds,
        p50_ms=percentile(ordered, 50),
        p95_ms=percentile(ordered, 95),
        p99_ms=percentile(ordered, 99),
        errors=errors,
    )


def evaluate_load_test(
    *,
    model_id: str,
    model_version: str,
    dimensions: int,
    startup_seconds: float,
    peak_rss_bytes: int,
    container_peak_memory_bytes: int,
    endpoints: tuple[EndpointMetrics, ...],
    thresholds: LoadThresholds = LoadThresholds(),
) -> LoadTestReport:
    failures: list[str] = []
    if startup_seconds > thresholds.max_startup_seconds:
        failures.append("STARTUP_TIME_EXCEEDED")
    if peak_rss_bytes > thresholds.max_peak_rss_bytes:
        failures.append("PEAK_RSS_EXCEEDED")
    for endpoint in endpoints:
        prefix = endpoint.name.upper().replace("-", "_")
        if endpoint.error_rate > thresholds.max_error_rate:
            failures.append(prefix + "_ERROR_RATE_EXCEEDED")
        if endpoint.p95_ms is None or endpoint.p95_ms > thresholds.max_p95_ms:
            failures.append(prefix + "_P95_EXCEEDED")
        if endpoint.throughput_rps < thresholds.min_throughput_rps:
            failures.append(prefix + "_THROUGHPUT_BELOW_MINIMUM")
    return LoadTestReport(
        contract_version="1.0.0",
        model_id=model_id,
        model_version=model_version,
        dimensions=dimensions,
        startup_seconds=startup_seconds,
        peak_rss_bytes=peak_rss_bytes,
        container_peak_memory_bytes=container_peak_memory_bytes,
        thresholds=thresholds,
        endpoints=endpoints,
        failures=tuple(failures),
    )


def write_load_test_report(
    report: LoadTestReport, json_path: Path, markdown_path: Path
) -> None:
    payload = asdict(report)
    payload["passed"] = report.passed
    _atomic_write(json_path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    rows = "\n".join(
        f"| {item.name} | {item.concurrency} | {item.request_count} | "
        f"{item.p50_ms:.2f} | {item.p95_ms:.2f} | {item.throughput_rps:.2f} | "
        f"{item.error_rate:.4f} |"
        for item in report.endpoints
        if item.p50_ms is not None and item.p95_ms is not None
    )
    markdown = (
        "# Arctic-ko retrieval load test\n\n"
        f"- Result: {'PASS' if report.passed else 'FAIL'}\n"
        f"- Model: `{report.model_version}`\n"
        f"- Startup: {report.startup_seconds:.2f} s\n"
        f"- Peak RSS: {report.peak_rss_bytes / 1024 / 1024:.2f} MiB\n"
        "- Container peak memory (including page cache): "
        f"{report.container_peak_memory_bytes / 1024 / 1024:.2f} MiB\n"
        f"- Failures: {', '.join(report.failures) if report.failures else 'none'}\n\n"
        "| Endpoint | Concurrency | Requests | p50 ms | p95 ms | RPS | Error rate |\n"
        "|---|---:|---:|---:|---:|---:|---:|\n"
        f"{rows}\n"
    )
    _atomic_write(markdown_path, markdown)


def percentile(ordered_values: list[float], percentage: int) -> float | None:
    if not ordered_values:
        return None
    if percentage < 0 or percentage > 100:
        raise ValueError("percentage must be between 0 and 100")
    index = max(0, math.ceil(percentage / 100 * len(ordered_values)) - 1)
    return ordered_values[index]


def _request_once(
    url: str,
    headers: Mapping[str, str],
    payload: dict[str, Any],
    response_validator: ResponseValidator,
    timeout_seconds: float,
) -> tuple[float, str | None]:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={**headers, "Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = json.load(response)
        latency = (time.perf_counter() - started) * 1_000
        if not isinstance(body, dict) or not response_validator(body):
            return latency, "RESPONSE_CONTRACT_INVALID"
        return latency, None
    except urllib.error.HTTPError as error:
        return (time.perf_counter() - started) * 1_000, f"HTTP_{error.code}"
    except (OSError, TimeoutError, json.JSONDecodeError):
        return (time.perf_counter() - started) * 1_000, "REQUEST_FAILED"


def _atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(path)
