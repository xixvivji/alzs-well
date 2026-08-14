package com.alzswell.demo.api;

import java.time.OffsetDateTime;
import java.util.List;

public record SyntheticDataProvenance(boolean syntheticData, String sourceProvider,
                                      OffsetDateTime sourceUpdatedAt, String dataFreshness,
                                      String consentId, List<String> consentScope,
                                      String snapshotHash) {
}
