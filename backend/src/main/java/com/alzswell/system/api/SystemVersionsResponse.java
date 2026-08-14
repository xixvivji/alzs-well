package com.alzswell.system.api;

import java.time.LocalDate;

public record SystemVersionsResponse(
        String applicationVersion,
        String apiVersion,
        String schemaVersion,
        String fixtureVersion,
        String algorithmVersion,
        String policyVersion,
        LocalDate sourceCatalogCheckedAt
) {
}
