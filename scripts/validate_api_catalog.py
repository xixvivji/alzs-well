#!/usr/bin/env python3
"""FINAL_BACKEND_API_SPEC의 마스터 API 카탈로그 집계를 검증한다."""

from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs" / "FINAL_BACKEND_API_SPEC.md"
EXPECTED_PRIORITIES = {"P0-A": 12, "P0-B": 11, "P1": 156, "P2": 78}
EXPECTED_BOUNDARIES = {
    "OWNED": 167,
    "EXTERNAL_INTEGRATION": 68,
    "REFERENCE_ONLY": 22,
}
ROW = re.compile(
    r"^\|\s*(P0-A|P0-B|P1|P2)\s*\|\s*"
    r"(GET|POST|PUT|PATCH|DELETE)\s*\|\s*([^|]+?)\s*\|[^|]*\|\s*"
    r"(OWNED|EXTERNAL_INTEGRATION|REFERENCE_ONLY)\s*\|$"
)


def fail(message: str) -> None:
    print(f"API catalog validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    document = SPEC.read_text(encoding="utf-8")
    try:
        catalog = document.split("### 3.3 도메인별 API", 1)[1].split(
            "### 3.4 구현 순서", 1
        )[0]
    except IndexError:
        fail("3.3 또는 3.4 절 경계를 찾을 수 없습니다.")

    rows = []
    for line in catalog.splitlines():
        if match := ROW.match(line):
            priority, method, path, boundary = match.groups()
            rows.append((priority, method, path.strip(), boundary))

    if len(rows) != 257:
        fail(f"operation 수가 257이 아니라 {len(rows)}입니다.")

    operations = Counter((method, path) for _, method, path, _ in rows)
    duplicates = sorted(operation for operation, count in operations.items() if count > 1)
    if duplicates:
        fail(f"Method + Path 중복이 있습니다: {duplicates}")

    priorities = Counter(priority for priority, _, _, _ in rows)
    if dict(priorities) != EXPECTED_PRIORITIES:
        fail(f"우선순위 집계가 다릅니다: {dict(priorities)}")

    boundaries = Counter(boundary for _, _, _, boundary in rows)
    if dict(boundaries) != EXPECTED_BOUNDARIES:
        fail(f"구현 경계 집계가 다릅니다: {dict(boundaries)}")

    print(
        "API catalog valid: "
        f"{len(rows)} operations, priorities={dict(priorities)}, "
        f"boundaries={dict(boundaries)}"
    )


if __name__ == "__main__":
    main()
