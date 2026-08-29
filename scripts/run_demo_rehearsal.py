#!/usr/bin/env python3
"""정상·주의·오탐 UI 계약과 RAG 장애 폴백을 한 번에 리허설한다."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def run(label: str, command: list[str], cwd: Path) -> None:
    print(f"\n[{label}] {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="세 가지 데모 상태와 AI citation·장애 폴백을 재현합니다."
    )
    parser.add_argument(
        "--contracts-only",
        action="store_true",
        help="Docker 통합 리허설을 생략하고 백엔드·프런트 계약만 빠르게 확인합니다.",
    )
    args = parser.parse_args()

    run(
        "백엔드 상태머신",
        ["./gradlew", "test", "--tests", "com.alzswell.demo.P0WorkflowIntegrationTest"],
        ROOT / "backend",
    )
    run("프런트 UI 계약", ["npm", "test"], ROOT / "frontend")
    if not args.contracts_only:
        run("전체 Compose 리허설", ["python3", "scripts/copilot_rag_e2e.py"], ROOT)

    scope = "계약" if args.contracts_only else "전체"
    print(f"\n{scope} 데모 리허설을 통과했습니다.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
