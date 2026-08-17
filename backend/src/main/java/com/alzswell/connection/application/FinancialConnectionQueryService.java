package com.alzswell.connection.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.connection.api.ConnectionErrorCode;
import com.alzswell.connection.api.ConnectionResponses.ConnectionDetail;
import com.alzswell.connection.api.ConnectionResponses.ConnectionList;
import com.alzswell.connection.api.ConnectionResponses.ConnectionSummary;
import com.alzswell.connection.api.ConnectionResponses.InstitutionDetail;
import com.alzswell.connection.api.ConnectionResponses.InstitutionList;
import com.alzswell.connection.api.ConnectionResponses.InstitutionSummary;
import com.alzswell.connection.api.ConnectionResponses.Scope;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialConnectionQueryService {
    private final JdbcTemplate jdbcTemplate;

    public FinancialConnectionQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public InstitutionList institutions() {
        List<InstitutionSummary> items = jdbcTemplate.query("""
                select institution_id, display_name, institution_type, provider_mode,
                       connection_available, data_as_of
                  from financial_institution
                 order by display_name, institution_id
                """, (rs, rowNum) -> institution(
                        rs.getString("institution_id"), rs.getString("display_name"),
                        rs.getString("institution_type"), rs.getString("provider_mode"),
                        rs.getBoolean("connection_available"), rs.getObject("data_as_of", LocalDate.class)));
        return new InstitutionList(items, items.size());
    }

    @Transactional(readOnly = true)
    public InstitutionDetail institution(String institutionId) {
        List<InstitutionSummary> rows = jdbcTemplate.query("""
                select institution_id, display_name, institution_type, provider_mode,
                       connection_available, data_as_of
                  from financial_institution where institution_id = ?
                """, (rs, rowNum) -> institution(
                        rs.getString("institution_id"), rs.getString("display_name"),
                        rs.getString("institution_type"), rs.getString("provider_mode"),
                        rs.getBoolean("connection_available"), rs.getObject("data_as_of", LocalDate.class)),
                institutionId);
        if (rows.size() != 1) throw new BusinessException(ConnectionErrorCode.INSTITUTION_NOT_FOUND);
        List<Scope> scopes = jdbcTemplate.query("""
                select scope_code, display_name, read_only
                  from financial_institution_scope where institution_id = ? order by scope_code
                """, (rs, rowNum) -> new Scope(rs.getString("scope_code"), rs.getString("display_name"),
                        rs.getBoolean("read_only"), null), institutionId);
        return new InstitutionDetail(rows.getFirst(), scopes);
    }

    @Transactional(readOnly = true)
    public ConnectionList connections(String customerId) {
        List<ConnectionSummary> items = queryConnections("where c.customer_id = ?", customerId);
        return new ConnectionList(items, items.size());
    }

    @Transactional(readOnly = true)
    public ConnectionDetail connection(String customerId, UUID connectionId) {
        List<ConnectionSummary> rows = queryConnections(
                "where c.customer_id = ? and c.connection_id = ?", customerId, connectionId);
        if (rows.size() != 1) throw new BusinessException(ConnectionErrorCode.CONNECTION_NOT_FOUND);
        List<Scope> scopes = jdbcTemplate.query("""
                select cs.scope_code, fs.display_name, fs.read_only, cs.consent_status
                  from customer_connection_scope cs
                  join customer_connection c on c.connection_id = cs.connection_id
                  join financial_institution_scope fs
                    on fs.institution_id = c.institution_id and fs.scope_code = cs.scope_code
                 where cs.connection_id = ? order by cs.scope_code
                """, (rs, rowNum) -> new Scope(rs.getString("scope_code"), rs.getString("display_name"),
                        rs.getBoolean("read_only"), rs.getString("consent_status")), connectionId);
        return new ConnectionDetail(rows.getFirst(), scopes);
    }

    private List<ConnectionSummary> queryConnections(String where, Object... arguments) {
        return jdbcTemplate.query("""
                select c.connection_id, c.customer_id, c.connection_status, c.consented_at,
                       c.consent_expires_at, c.last_synced_at, c.provider_mode, c.row_version,
                       i.institution_id, i.display_name, i.institution_type,
                       i.provider_mode institution_provider_mode, i.connection_available, i.data_as_of
                  from customer_connection c
                  join financial_institution i on i.institution_id = c.institution_id
                """ + where + " order by i.display_name, c.connection_id",
                (rs, rowNum) -> new ConnectionSummary(
                        rs.getObject("connection_id", UUID.class), rs.getString("customer_id"),
                        institution(rs.getString("institution_id"), rs.getString("display_name"),
                                rs.getString("institution_type"), rs.getString("institution_provider_mode"),
                                rs.getBoolean("connection_available"), rs.getObject("data_as_of", LocalDate.class)),
                        rs.getString("connection_status"), rs.getObject("consented_at", OffsetDateTime.class),
                        rs.getObject("consent_expires_at", OffsetDateTime.class),
                        rs.getObject("last_synced_at", OffsetDateTime.class), rs.getString("provider_mode"),
                        rs.getLong("row_version")), arguments);
    }

    private InstitutionSummary institution(String id, String name, String type, String provider,
                                             boolean available, LocalDate dataAsOf) {
        return new InstitutionSummary(id, name, type, provider, available, dataAsOf);
    }
}
