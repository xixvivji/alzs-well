package com.alzswell.common.idempotency;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class MutationIdempotencyService {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MutationIdempotencyService(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public <T> T execute(String scope, String idempotencyKey, Object request, Class<T> responseType,
            ErrorCode conflictCode, Supplier<T> action) {
        String keyHash = sha256(idempotencyKey);
        String requestHash = sha256(json(request));
        int inserted = jdbc.sql("""
                insert into customer_mutation_command(
                    command_scope,idempotency_key_hash,request_hash,created_at
                ) values(?,?,?,?) on conflict do nothing
                """).params(scope, keyHash, requestHash, OffsetDateTime.now(clock)).update();
        if (inserted == 0) return replay(scope, keyHash, requestHash, responseType, conflictCode);
        T result = action.get();
        int completed = jdbc.sql("""
                update customer_mutation_command set result_payload=?::jsonb,completed_at=?
                 where command_scope=? and idempotency_key_hash=? and result_payload is null
                """).params(json(result), OffsetDateTime.now(clock), scope, keyHash).update();
        if (completed != 1) throw new BusinessException(conflictCode);
        return result;
    }

    private <T> T replay(String scope, String keyHash, String requestHash, Class<T> responseType,
            ErrorCode conflictCode) {
        Command row = jdbc.sql("""
                select request_hash,result_payload::text from customer_mutation_command
                 where command_scope=? and idempotency_key_hash=?
                """).params(scope, keyHash).query((rs, n) -> new Command(rs.getString(1), rs.getString(2)))
                .optional().orElseThrow(() -> new BusinessException(conflictCode));
        if (!secureEquals(row.requestHash(), requestHash) || row.resultPayload() == null) {
            throw new BusinessException(conflictCode);
        }
        try { return objectMapper.readValue(row.resultPayload(), responseType); }
        catch (Exception exception) { throw new IllegalStateException("멱등 응답을 복원할 수 없습니다.", exception); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("멱등 요청을 직렬화할 수 없습니다.", exception); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private record Command(String requestHash, String resultPayload) {}
}
