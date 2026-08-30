package com.alzswell.support.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SupportResponses {
    private SupportResponses() {}

    public record Faq(
            UUID faqId,
            String categoryCode,
            String question,
            String answer,
            int displayOrder,
            LocalDate dataAsOf
    ) {}

    public record FaqList(
            List<Faq> items,
            int total,
            boolean syntheticData,
            boolean externalProviderCalled,
            boolean externalActionExecuted
    ) {}

    public record Notice(
            UUID noticeId,
            String institutionId,
            String institutionName,
            String categoryCode,
            String title,
            String body,
            boolean important,
            OffsetDateTime publishedAt,
            OffsetDateTime expiresAt,
            LocalDate dataAsOf
    ) {}

    public record NoticeList(
            List<Notice> items,
            int total,
            LocalDate asOf,
            boolean syntheticData,
            boolean externalProviderCalled,
            boolean externalActionExecuted
    ) {}
}
