package com.alzswell.support.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.support.api.SupportErrorCode;
import com.alzswell.support.api.SupportResponses.Faq;
import com.alzswell.support.api.SupportResponses.FaqList;
import com.alzswell.support.api.SupportResponses.Notice;
import com.alzswell.support.api.SupportResponses.NoticeList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupportContentService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SupportContentService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public FaqList faqs(String category, int limit) {
        StringBuilder sql = new StringBuilder("""
                select faq_id, category_code, question, answer_text, display_order, data_as_of
                  from support_faq_snapshot
                 where status = 'PUBLISHED'
                """);
        List<Object> args = new ArrayList<>();
        if (category != null) {
            sql.append(" and category_code = ?");
            args.add(category);
        }
        sql.append(" order by category_code, display_order, faq_id limit ?");
        args.add(limit);
        List<Faq> items = jdbc.query(sql.toString(), this::faqRow, args.toArray());
        return new FaqList(items, items.size(), true, false, false);
    }

    public NoticeList notices(String category, LocalDate from, LocalDate to, int limit) {
        validatePeriod(from, to);
        LocalDate asOf = LocalDate.now(clock.withZone(SEOUL));
        StringBuilder sql = new StringBuilder("""
                select n.notice_id, n.institution_id, i.display_name institution_name,
                       n.category_code, n.title, n.body_text, n.important,
                       n.published_at, n.expires_at, n.data_as_of
                  from support_notice_snapshot n
                  join financial_institution i on i.institution_id = n.institution_id
                 where n.status = 'PUBLISHED'
                   and (n.expires_at is null or n.expires_at >= ?)
                """);
        List<Object> args = new ArrayList<>();
        args.add(asOf.atStartOfDay(SEOUL).toOffsetDateTime());
        if (category != null) {
            sql.append(" and n.category_code = ?");
            args.add(category);
        }
        if (from != null) {
            sql.append(" and n.published_at >= ?");
            args.add(from.atStartOfDay(SEOUL).toOffsetDateTime());
        }
        if (to != null) {
            sql.append(" and n.published_at < ?");
            args.add(to.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime());
        }
        sql.append(" order by n.important desc, n.published_at desc, n.notice_id desc limit ?");
        args.add(limit);
        List<Notice> items = jdbc.query(sql.toString(), this::noticeRow, args.toArray());
        return new NoticeList(items, items.size(), asOf, true, false, false);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null
                && (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 365)) {
            throw new BusinessException(SupportErrorCode.INVALID_NOTICE_PERIOD);
        }
    }

    private Faq faqRow(ResultSet row, int number) throws SQLException {
        return new Faq(
                row.getObject("faq_id", java.util.UUID.class),
                row.getString("category_code"),
                row.getString("question"),
                row.getString("answer_text"),
                row.getInt("display_order"),
                row.getObject("data_as_of", LocalDate.class)
        );
    }

    private Notice noticeRow(ResultSet row, int number) throws SQLException {
        return new Notice(
                row.getObject("notice_id", java.util.UUID.class),
                row.getString("institution_id"),
                row.getString("institution_name"),
                row.getString("category_code"),
                row.getString("title"),
                row.getString("body_text"),
                row.getBoolean("important"),
                row.getObject("published_at", java.time.OffsetDateTime.class),
                row.getObject("expires_at", java.time.OffsetDateTime.class),
                row.getObject("data_as_of", LocalDate.class)
        );
    }
}
