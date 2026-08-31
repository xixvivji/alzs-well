from __future__ import annotations

import re
import sys
from pathlib import Path


REVIEWER_PATTERN = re.compile(r"^[a-z0-9][a-z0-9._:-]{2,119}$")
README_LINK_PATTERN = re.compile(r"\]\((\./[^)#]+)(?:#[^)]+)?\)")


def validate_manifest_reviewers(repository_root: Path) -> list[str]:
    errors: list[str] = []
    for manifest in sorted((repository_root / "knowledge/manifests").glob("*.yaml")):
        for line_number, line in enumerate(
            manifest.read_text(encoding="utf-8").splitlines(), start=1
        ):
            if not line.startswith("approvedBy:"):
                continue
            reviewer = line.partition(":")[2].strip().strip('"').strip("'")
            if reviewer == "null":
                continue
            if "@" in reviewer:
                errors.append(
                    f"{manifest.relative_to(repository_root)}:{line_number}: "
                    "공개 manifest에 개인 이메일을 승인자 식별자로 기록할 수 없습니다"
                )
            elif not REVIEWER_PATTERN.fullmatch(reviewer):
                errors.append(
                    f"{manifest.relative_to(repository_root)}:{line_number}: "
                    "승인자는 공개 가능한 안정적 감사 식별자여야 합니다"
                )
    return errors


def validate_readme_links(repository_root: Path) -> list[str]:
    errors: list[str] = []
    readme = repository_root / "README.md"
    payload = readme.read_text(encoding="utf-8")
    for relative_link in README_LINK_PATTERN.findall(payload):
        target = repository_root / relative_link.removeprefix("./")
        if not target.exists():
            errors.append(f"README.md: 존재하지 않는 로컬 경로를 참조합니다: {relative_link}")
    return errors


def main() -> int:
    repository_root = Path(__file__).resolve().parents[1]
    errors = [
        *validate_manifest_reviewers(repository_root),
        *validate_readme_links(repository_root),
    ]
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("공개 저장소 메타데이터 검증 통과")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
