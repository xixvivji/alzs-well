from __future__ import annotations

import re
from pathlib import Path


MIGRATION = "V68__create_multi_dimension_chunk_embeddings.sql"


def test_multi_dimension_migration_separates_model_version_indexes(
    repo_root: Path,
) -> None:
    sql = (
        repo_root / "backend/src/main/resources/db/migration" / MIGRATION
    ).read_text(encoding="utf-8")

    assert "create table ai_knowledge.chunk_embedding" in sql
    assert "embedding vector not null" in sql
    assert "embedding_dimensions in (384, 1024)" in sql
    assert "vector_dims(embedding) = embedding_dimensions" in sql
    assert "references ai_knowledge.chunk(chunk_id) on delete cascade" in sql
    assert "grant update(" in sql
    assert ") on ai_knowledge.chunk to alzswell_ai_ingestor" in sql
    assert "on ai_knowledge.chunk_embedding to alzswell_ai_ingestor" in sql
    assert "embedding::vector(384)" in sql
    assert "embedding::vector(1024)" in sql
    assert "local-hash-ngram-ko-v1" in sql
    assert "multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3" in sql
    assert (
        "snowflake-arctic-embed-l-v2.0-ko@55ec6e9358a56d56af759bc8372e970caf8c305f"
        in sql
    )
    assert len(re.findall(r"using hnsw", sql, flags=re.IGNORECASE)) == 3


def test_migration_backfills_hash_and_preserves_legacy_columns(repo_root: Path) -> None:
    sql = (
        repo_root / "backend/src/main/resources/db/migration" / MIGRATION
    ).read_text(encoding="utf-8")

    assert "insert into ai_knowledge.chunk_embedding" in sql
    assert "from ai_knowledge.chunk" in sql
    assert "drop column embedding" not in sql.lower()
    assert "하위 호환용 384차원 Hash 벡터" in sql
