from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path

from app.domain.chunk import KnowledgeChunk
from app.errors import KnowledgeContractError


def write_chunks_jsonl(repository_root: Path, chunks: tuple[KnowledgeChunk, ...]) -> Path:
    if not chunks:
        raise KnowledgeContractError("CHUNK_VALIDATION_FAILED")

    first = chunks[0]
    output_directory = repository_root / "ai-service" / "data" / "derived" / "chunks"
    output_path = output_directory / f"{first.document_id}-{first.version_label}.jsonl"
    temporary_path: Path | None = None
    try:
        output_directory.mkdir(parents=True, exist_ok=True)
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{output_path.name}.", suffix=".tmp", dir=output_directory
        )
        temporary_path = Path(temporary_name)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            for chunk in chunks:
                json.dump(
                    chunk.as_json_object(),
                    stream,
                    ensure_ascii=False,
                    sort_keys=True,
                    separators=(",", ":"),
                )
                stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, output_path)
        temporary_path = None
        return output_path
    except (OSError, TypeError, ValueError):
        raise KnowledgeContractError("OUTPUT_WRITE_FAILED") from None
    finally:
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError:
                pass
