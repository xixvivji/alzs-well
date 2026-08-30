package com.alzswell.knowledge.api;

import java.time.OffsetDateTime;
import java.util.*;

public final class KnowledgeImportResponses {
    private KnowledgeImportResponses() {}
    public record ImportResult(UUID importId,UUID ingestionRunId,String documentId,String versionLabel,
            int chunkCount,List<UUID> passageIds,boolean searchable,OffsetDateTime importedAt) {}
}
