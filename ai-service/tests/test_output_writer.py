from __future__ import annotations

import json
import os
from pathlib import Path

import pytest

from app.domain.chunk import KnowledgeChunk
from app.errors import KnowledgeContractError
from app.ingestion.output_writer import write_chunks_jsonl


def test_writes_utf8_jsonl_and_atomically_replaces_existing_file(tmp_path: Path) -> None:
    chunks = (_chunk("chk_" + "1" * 64, 1, "첫 본문"), _chunk("chk_" + "2" * 64, 2, "둘째 본문"))

    output = write_chunks_jsonl(tmp_path, chunks)
    first_bytes = output.read_bytes()
    output.write_text("incomplete", encoding="utf-8")
    replaced = write_chunks_jsonl(tmp_path, chunks)

    assert replaced == output
    assert replaced.read_bytes() == first_bytes
    rows = [json.loads(line) for line in replaced.read_text(encoding="utf-8").splitlines()]
    assert [row["chunkOrder"] for row in rows] == [1, 2]
    assert rows[0]["text"] == "첫 본문"
    assert not list(output.parent.glob("*.tmp"))


def test_rejects_empty_chunk_collection(tmp_path: Path) -> None:
    with pytest.raises(KnowledgeContractError) as caught:
        write_chunks_jsonl(tmp_path, ())

    assert caught.value.code == "CHUNK_VALIDATION_FAILED"


def test_failed_replace_keeps_previous_file_and_removes_temporary_file(
    tmp_path: Path, monkeypatch: object
) -> None:
    chunks = (_chunk("chk_" + "1" * 64, 1, "새 본문"),)
    output_directory = tmp_path / "ai-service/data/derived/chunks"
    output_directory.mkdir(parents=True)
    output = output_directory / "DOC-SYN-WRITE-001-1.0.0.jsonl"
    output.write_text("previous\n", encoding="utf-8")

    def fail_replace(source: object, target: object) -> None:
        raise OSError("synthetic replace failure")

    monkeypatch.setattr(os, "replace", fail_replace)  # type: ignore[attr-defined]
    with pytest.raises(KnowledgeContractError) as caught:
        write_chunks_jsonl(tmp_path, chunks)

    assert caught.value.code == "OUTPUT_WRITE_FAILED"
    assert output.read_text(encoding="utf-8") == "previous\n"
    assert not list(output_directory.glob("*.tmp"))


def _chunk(chunk_id: str, order: int, text: str) -> KnowledgeChunk:
    return KnowledgeChunk(
        chunk_id=chunk_id,
        document_id="DOC-SYN-WRITE-001",
        version_label="1.0.0",
        heading="절",
        section_path=("문서", "절"),
        page=None,
        chunk_order=order,
        text=text,
        text_hash="sha256:" + "3" * 64,
        source_hash="sha256:" + "4" * 64,
        extractor_version="html-structure-v1",
        chunker_version="structure-ko-v1",
    )
