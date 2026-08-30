from __future__ import annotations

from pathlib import Path

import pytest

from app.domain.manifest import KnowledgeManifest
from app.errors import KnowledgeContractError
from app.ingestion.html_extractor import extract_html_document
from app.ingestion.manifest_loader import load_and_validate_manifest
from app.ingestion.source_validator import ValidatedSource, validate_source


def test_extracts_synthetic_fixture_with_heading_hierarchy(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(
        repo_root, "contracts/knowledge/fixtures/synthetic-approved-active.yaml"
    )
    source = validate_source(repo_root, manifest)

    document = extract_html_document(manifest, source)

    assert document.title == "합성 안심 안내"
    assert [(block.block_type, block.text) for block in document.blocks] == [
        ("HEADING", "신청 방법"),
        ("PARAGRAPH", "이 문서는 계약 검증을 위한 합성 자료입니다."),
    ]
    assert document.blocks[1].section_path == ("합성 안심 안내", "신청 방법")
    assert document.warnings == ()


def test_extracts_only_official_article_body(repo_root: Path) -> None:
    manifest = load_and_validate_manifest(
        repo_root, "knowledge/manifests/DOC-FSC-SAFE-BLOCK-001.yaml"
    )
    source = validate_source(repo_root, manifest)

    document = extract_html_document(manifest, source)
    extracted_text = "\n".join(block.text for block in document.blocks)

    assert document.title == "'비대면 계좌개설·여신거래 안심차단'하세요(명의도용 금융피해 예방)"
    assert "비대면 계좌개설·여신거래 안심차단 서비스" in extracted_text
    assert "0429_안심차단서비스_한컷-01.png" not in extracted_text
    assert "REDACTED_SOURCE_CREDENTIAL" not in extracted_text
    assert "SOURCE_CREDENTIAL_REDACTION_PRESENT" in document.warnings
    assert "NO_STRUCTURAL_HEADING" in document.warnings


def test_removes_noise_and_preserves_nested_sections() -> None:
    document = _extract(
        """<!doctype html><html><head><title>문서</title><style>.x{}</style></head>
        <body><nav>메뉴</nav><main><h1>문서</h1><h2>대분류</h2><p>본문</p>
        <h3>소분류</h3><ul><li>첫째<ul><li>중첩</li></ul></li><li>둘째</li></ul>
        <form>입력</form><div aria-hidden='true'>숨김</div><script>위험</script></main>
        <footer>푸터</footer></body></html>"""
    )

    texts = [block.text for block in document.blocks]
    assert texts == ["대분류", "본문", "소분류", "첫째 중첩", "둘째"]
    assert document.blocks[3].block_type == "LIST_ITEM"
    assert document.blocks[3].section_path == ("문서", "대분류", "소분류")
    assert not any(noise in " ".join(texts) for noise in ("메뉴", "입력", "숨김", "위험", "푸터"))


def test_skips_a_block_containing_redacted_credential() -> None:
    document = _extract(
        """<!doctype html><html><head><title>문서</title></head><body><main>
        <h1>문서</h1><p>보존할 문장</p><p>키 REDACTED_SOURCE_CREDENTIAL 제거</p>
        </main></body></html>"""
    )

    assert [block.text for block in document.blocks] == ["보존할 문장"]
    assert document.warnings == (
        "SOURCE_CREDENTIAL_REDACTION_PRESENT",
        "REDACTED_CREDENTIAL_BLOCK_SKIPPED",
        "NO_STRUCTURAL_HEADING",
    )


def test_reports_generic_body_fallback() -> None:
    document = _extract(
        "<!doctype html><html><head><title>문서</title></head><body><p>본문</p></body></html>"
    )

    assert document.blocks[0].text == "본문"
    assert "GENERIC_BODY_FALLBACK_USED" in document.warnings


@pytest.mark.parametrize(
    "html",
    [
        "<!doctype html><html><head><title>문서</title></head><body><main></main></body></html>",
        "<!doctype html><html><head></head><body><main><p>본문</p></main></body></html>",
    ],
)
def test_rejects_document_without_extractable_content(html: str) -> None:
    with pytest.raises(KnowledgeContractError) as caught:
        _extract(html)

    assert caught.value.code == "NO_EXTRACTABLE_CONTENT"


def _extract(html: str) -> object:
    manifest = KnowledgeManifest(
        {
            "contractVersion": "1.0.0",
            "documentId": "DOC-SYN-EXTRACT-001",
            "versionLabel": "1.0.0",
        }
    )
    source = ValidatedSource(
        path=Path("synthetic.html"),
        size_bytes=len(html.encode("utf-8")),
        source_hash="sha256:" + "0" * 64,
        encoding="UTF-8",
        text=html,
    )
    return extract_html_document(manifest, source)
