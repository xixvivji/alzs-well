package com.alzswell.knowledge.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class KnowledgeRequests {
    public record SearchCommand(
            @NotBlank @Size(min=2,max=120) String query,
            @NotNull LocalDate asOf,
            @NotBlank @Pattern(regexp="CUSTOMER|STAFF") String audience,
            @Min(1) @Max(20) Integer limit) {
        public int resolvedLimit(){return limit==null?10:limit;}
    }
    private KnowledgeRequests(){}
}
