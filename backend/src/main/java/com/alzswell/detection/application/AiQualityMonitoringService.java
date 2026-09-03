package com.alzswell.detection.application;

import static com.alzswell.detection.api.AiQualityResponses.AiQualitySummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQualityMonitoringService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiQualityMonitoringService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiQualitySummary summary(int windowHours) {
        OffsetDateTime to = OffsetDateTime.now(clock);
        OffsetDateTime from = to.minusHours(windowHours);
        Map<String, Object> search = jdbc.queryForMap("""
                select count(*) search_requests,
                       count(*) filter(where detail->>'retrievalMode'='INTERNAL_RAG_HYBRID') grounded_searches,
                       count(*) filter(where coalesce((detail->>'fallbackUsed')::boolean,false)) fallback_searches,
                       count(*) filter(where coalesce((detail->>'total')::int,0)=0) empty_searches,
                       coalesce(sum(coalesce((detail->>'rejectedCitations')::int,0)),0) rejected_citations
                  from knowledge_access_audit_event
                 where event_type='SEARCH' and occurred_at>=? and occurred_at<=?
                """, from, to);
        Map<String, Object> assistance = jdbc.queryForMap("""
                select count(*) assistance_requests,
                       count(*) filter(where event_type='DEMO_AI_ASSISTANCE_GENERATED') assistance_generated,
                       count(*) filter(where event_type='DEMO_AI_ASSISTANCE_FALLBACK_USED') assistance_fallbacks
                  from decision_audit
                 where event_type in ('DEMO_AI_ASSISTANCE_GENERATED','DEMO_AI_ASSISTANCE_FALLBACK_USED')
                   and occurred_at>=? and occurred_at<=?
                """, from, to);
        long searches = number(search, "search_requests");
        long searchFallbacks = number(search, "fallback_searches");
        long assistanceRequests = number(assistance, "assistance_requests");
        long assistanceFallbacks = number(assistance, "assistance_fallbacks");
        BigDecimal searchRate = rate(searchFallbacks, searches);
        BigDecimal assistanceRate = rate(assistanceFallbacks, assistanceRequests);
        String status = searches + assistanceRequests == 0 ? "NO_DATA"
                : searchRate.compareTo(new BigDecimal("0.10")) > 0
                || assistanceRate.compareTo(new BigDecimal("0.10")) > 0
                || number(search, "rejected_citations") > 0 ? "ATTENTION" : "HEALTHY";
        return new AiQualitySummary(windowHours, from, to, status, searches,
                number(search, "grounded_searches"), searchFallbacks,
                number(search, "empty_searches"), number(search, "rejected_citations"), searchRate,
                assistanceRequests, number(assistance, "assistance_generated"), assistanceFallbacks,
                assistanceRate, true, false);
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
