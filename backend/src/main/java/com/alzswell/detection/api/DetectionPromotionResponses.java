package com.alzswell.detection.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DetectionPromotionResponses {
    private DetectionPromotionResponses() {}

    public record DetectionPromotion(
            UUID promotionId, UUID detectionRunId, String customerId, String status,
            List<UUID> signalIds, List<UUID> alertIds, int promotedSignalCount,
            int promotedAlertCount, String inputResultHash, String promotionResultHash,
            OffsetDateTime promotedAt, boolean idempotencyReplayed,
            boolean financialActionExecuted, boolean externalNotificationSent
    ) {}
}
