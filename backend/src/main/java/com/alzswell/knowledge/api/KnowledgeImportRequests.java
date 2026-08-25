package com.alzswell.knowledge.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public final class KnowledgeImportRequests {
    private KnowledgeImportRequests() {}

    public record ImportChunk(
            @NotBlank @Pattern(regexp="chk_[0-9a-f]{64}") String chunkId,
            @Positive int chunkOrder,
            @NotBlank @Size(max=240) String heading,
            @NotNull @Size(max=16) List<@NotBlank @Size(max=240) String> sectionPath,
            @Positive Integer page,
            @Positive Integer pageStart,
            @Positive Integer pageEnd,
            @NotBlank @Size(max=1200) String text,
            @NotBlank @Pattern(regexp="sha256:[0-9a-f]{64}") String textHash,
            @NotBlank @Pattern(regexp="sha256:[0-9a-f]{64}") String sourceHash,
            @NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9._-]+") String extractorVersion,
            @NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9._-]+") String chunkerVersion
    ) {}

    public record ImportIngestionCommand(
            @NotBlank @Pattern(regexp="1[.]0[.]0") String contractVersion,
            @NotNull UUID ingestionRunId,
            @NotBlank @Pattern(regexp="[A-Z0-9]+(?:-[A-Z0-9]+)*") @Size(max=80) String documentId,
            @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{0,39}") String versionLabel,
            @NotBlank @Pattern(regexp="sha256:[0-9a-f]{64}") String sourceHash,
            @NotNull LocalDate asOf,
            @NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9._-]+") String extractorVersion,
            @NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9._-]+") String chunkerVersion,
            @NotEmpty @Size(max=500) List<@Valid ImportChunk> chunks
    ) {}
}
