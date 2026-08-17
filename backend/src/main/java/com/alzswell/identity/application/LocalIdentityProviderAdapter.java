package com.alzswell.identity.application;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.identity.api.AuthErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LocalIdentityProviderAdapter implements IdentityProviderPort {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public LocalIdentityProviderAdapter(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticatedPrincipal authenticate(String loginId, String password) {
        List<CredentialRow> rows = jdbcTemplate.query(
                "select principal_id, customer_id, password_hash from auth_principal where login_id = ? and status = 'ACTIVE'",
                (rs, rowNum) -> new CredentialRow(rs.getObject("principal_id", UUID.class),
                        rs.getString("customer_id"), rs.getString("password_hash")), loginId);
        if (rows.size() != 1 || !passwordEncoder.matches(password, rows.getFirst().passwordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        CredentialRow row = rows.getFirst();
        return new AuthenticatedPrincipal(row.principalId(), row.customerId());
    }

    private record CredentialRow(UUID principalId, String customerId, String passwordHash) {}
}
