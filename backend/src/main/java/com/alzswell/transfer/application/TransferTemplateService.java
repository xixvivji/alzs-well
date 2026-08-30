package com.alzswell.transfer.application;

import static com.alzswell.transfer.api.TransferTemplateErrorCode.IDEMPOTENCY_CONFLICT;
import static com.alzswell.transfer.api.TransferTemplateErrorCode.LIMIT_EXCEEDED;
import static com.alzswell.transfer.api.TransferTemplateErrorCode.NOT_FOUND;
import static com.alzswell.transfer.api.TransferTemplateErrorCode.RESOURCE_NOT_FOUND;

import com.alzswell.common.audit.AuditTimestamp;
import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.idempotency.MutationIdempotencyService;
import com.alzswell.common.security.AuditActor;
import com.alzswell.common.security.SensitiveTextPolicy;
import com.alzswell.transfer.api.TransferTemplateRequests;
import com.alzswell.transfer.api.TransferTemplateResponses.Deletion;
import com.alzswell.transfer.api.TransferTemplateResponses.Template;
import com.alzswell.transfer.api.TransferTemplateResponses.TemplateList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferTemplateService {
    private static final int MAX_TEMPLATES = 20;
    private static final String SELECT_TEMPLATE = """
            select t.template_id,t.template_name,t.source_account_id,a.display_name source_name,
                   a.masked_account_number,t.beneficiary_id,b.display_name beneficiary_name,
                   b.masked_account_reference,t.amount,t.currency,t.purpose_code,t.status,
                   t.row_version,t.created_at,t.deleted_at
              from customer_transfer_template t
              join customer_account_snapshot a on a.account_id=t.source_account_id and a.customer_id=t.customer_id
              join customer_beneficiary_snapshot b on b.beneficiary_id=t.beneficiary_id and b.customer_id=t.customer_id
            """;
    private final JdbcTemplate jdbc;
    private final MutationIdempotencyService idempotency;
    private final SensitiveTextPolicy sensitiveTextPolicy;
    private final Clock clock;

    public TransferTemplateService(JdbcTemplate jdbc, MutationIdempotencyService idempotency,
            SensitiveTextPolicy sensitiveTextPolicy, Clock clock) {
        this.jdbc = jdbc;
        this.idempotency = idempotency;
        this.sensitiveTextPolicy = sensitiveTextPolicy;
        this.clock = clock;
    }

    public TemplateList list(String customerId) {
        List<Template> items = jdbc.query(SELECT_TEMPLATE + """
                 where t.customer_id=? and t.status='ACTIVE'
                 order by t.template_name,t.created_at,t.template_id
                """, this::map, customerId);
        return new TemplateList(items, items.size(), MAX_TEMPLATES, true, false);
    }

    @Transactional
    public Template create(String customerId, TransferTemplateRequests.Create command, String key,
            AuditActor actor) {
        String normalizedName = sensitiveTextPolicy.validate(command.templateName(), "templateName");
        CreateRequest request = new CreateRequest(normalizedName, command.sourceAccountId(),
                command.beneficiaryId(), command.amount(), command.currency(), command.purposeCode());
        return idempotency.execute("TRANSFER_TEMPLATE_CREATE:" + customerId, key, request,
                Template.class, IDEMPOTENCY_CONFLICT, () -> createOnce(customerId, request, actor));
    }

    private Template createOnce(String customerId, CreateRequest command, AuditActor actor) {
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", resultSet -> {
            if (resultSet.next()) resultSet.getObject(1);
            return null;
        }, "transfer-template:" + customerId);
        Integer total = jdbc.queryForObject("""
                select count(*) from customer_transfer_template
                 where customer_id=? and status='ACTIVE'
                """, Integer.class, customerId);
        if (total != null && total >= MAX_TEMPLATES) throw new BusinessException(LIMIT_EXCEEDED);
        Integer resources = jdbc.queryForObject("""
                select count(*)
                  from customer_account_snapshot a
                  join customer_beneficiary_snapshot b on b.customer_id=a.customer_id
                 where a.customer_id=? and a.account_id=? and a.account_status='ACTIVE'
                   and b.beneficiary_id=? and b.status='ACTIVE'
                   and a.currency=?
                """, Integer.class, customerId, command.sourceAccountId(), command.beneficiaryId(),
                command.currency());
        if (resources == null || resources != 1) throw new BusinessException(RESOURCE_NOT_FOUND);

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        String integrity = hash(customerId + "|" + id + "|" + command + "|1");
        jdbc.update("""
                insert into customer_transfer_template(
                    template_id,customer_id,source_account_id,beneficiary_id,template_name,amount,
                    currency,purpose_code,status,row_version,created_at,deleted_at,integrity_hash
                ) values(?,?,?,?,?,?,?,?,'ACTIVE',1,?,null,?)
                """, id, customerId, command.sourceAccountId(), command.beneficiaryId(),
                command.templateName(), command.amount(), command.currency(), command.purposeCode(),
                now, integrity);
        event(id, customerId, "CREATED", command, "ACTIVE", 1, actor, now);
        return find(customerId, id);
    }

    @Transactional
    public Deletion delete(String customerId, UUID templateId, String key, AuditActor actor) {
        DeleteRequest request = new DeleteRequest(templateId);
        return idempotency.execute("TRANSFER_TEMPLATE_DELETE:" + customerId + ":" + templateId,
                key, request, Deletion.class, IDEMPOTENCY_CONFLICT,
                () -> deleteOnce(customerId, templateId, actor));
    }

    private Deletion deleteOnce(String customerId, UUID templateId, AuditActor actor) {
        List<Row> rows = jdbc.query(SELECT_TEMPLATE + """
                 where t.customer_id=? and t.template_id=? for update of t
                """, this::row, customerId, templateId);
        if (rows.size() != 1) throw new BusinessException(NOT_FOUND);
        Row row = rows.getFirst();
        if ("DELETED".equals(row.status())) {
            return new Deletion(templateId, row.status(), row.version(), row.deletedAt(), true, false);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        int changed = jdbc.update("""
                update customer_transfer_template
                   set status='DELETED',row_version=row_version+1,deleted_at=?
                 where template_id=? and customer_id=? and status='ACTIVE' and row_version=?
                """, now, templateId, customerId, row.version());
        if (changed != 1) throw new BusinessException(IDEMPOTENCY_CONFLICT);
        CreateRequest snapshot = new CreateRequest(row.templateName(), row.sourceAccountId(),
                row.beneficiaryId(), row.amount(), row.currency(), row.purposeCode());
        event(templateId, customerId, "DELETED", snapshot, "DELETED", row.version() + 1, actor, now);
        return new Deletion(templateId, "DELETED", row.version() + 1, now, false, false);
    }

    private Template find(String customerId, UUID id) {
        List<Template> rows = jdbc.query(SELECT_TEMPLATE + """
                 where t.customer_id=? and t.template_id=? and t.status='ACTIVE'
                """, this::map, customerId, id);
        if (rows.size() != 1) throw new BusinessException(NOT_FOUND);
        return rows.getFirst();
    }

    private void event(UUID templateId, String customerId, String eventType, CreateRequest snapshot,
            String status, long version, AuditActor actor, OffsetDateTime occurredAt) {
        occurredAt = AuditTimestamp.canonical(occurredAt);
        String eventHash = hash(templateId + "|" + eventType + "|" + status + "|" + version
                + "|" + snapshot + "|" + occurredAt);
        jdbc.update("""
                insert into customer_transfer_template_event(
                    event_id,template_id,customer_id,event_type,source_account_id,beneficiary_id,
                    template_name,amount,currency,purpose_code,status_snapshot,version_snapshot,
                    actor_subject,occurred_at,integrity_hash
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), templateId, customerId, eventType, snapshot.sourceAccountId(),
                snapshot.beneficiaryId(), snapshot.templateName(), snapshot.amount(), snapshot.currency(),
                snapshot.purposeCode(), status, version, actor.legacyActorId(), occurredAt, eventHash);
    }

    private Template map(java.sql.ResultSet result, int rowNumber) throws java.sql.SQLException {
        return template(row(result, rowNumber));
    }

    private Row row(java.sql.ResultSet result, int rowNumber) throws java.sql.SQLException {
        return new Row(result.getObject("template_id", UUID.class), result.getString("template_name"),
                result.getObject("source_account_id", UUID.class), result.getString("source_name"),
                result.getString("masked_account_number"), result.getObject("beneficiary_id", UUID.class),
                result.getString("beneficiary_name"), result.getString("masked_account_reference"),
                result.getBigDecimal("amount"), result.getString("currency"),
                result.getString("purpose_code"), result.getString("status"),
                result.getLong("row_version"), result.getObject("created_at", OffsetDateTime.class),
                result.getObject("deleted_at", OffsetDateTime.class));
    }

    private Template template(Row row) {
        return new Template(row.id(), row.templateName(), row.sourceAccountId(), row.sourceAccountName(),
                row.maskedSourceAccount(), row.beneficiaryId(), row.beneficiaryName(),
                row.maskedBeneficiaryAccount(), row.amount(), row.currency(), row.purposeCode(),
                row.status(), row.version(), row.createdAt(), true, false, false);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record CreateRequest(String templateName, UUID sourceAccountId, UUID beneficiaryId,
                                 java.math.BigDecimal amount, String currency, String purposeCode) {}
    private record DeleteRequest(UUID templateId) {}
    private record Row(UUID id, String templateName, UUID sourceAccountId, String sourceAccountName,
                       String maskedSourceAccount, UUID beneficiaryId, String beneficiaryName,
                       String maskedBeneficiaryAccount, java.math.BigDecimal amount, String currency,
                       String purposeCode, String status, long version, OffsetDateTime createdAt,
                       OffsetDateTime deletedAt) {}
}
