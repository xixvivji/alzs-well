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
    // 실제 계정과 같은 BCrypt cost를 사용해 존재하지 않는 loginId도 동일한 검증 경로를 거친다.
    private static final String DUMMY_PASSWORD_HASH =
            "$2y$12$Bu7SxonBbyIlnLnrupD/.eEWz3ZVBoC8bDvguOq9iJlsOAN8pGxBm";
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
        String passwordHash = rows.size() == 1 ? rows.getFirst().passwordHash() : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (rows.size() != 1 || !passwordMatches) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        CredentialRow row = rows.getFirst();
        return new AuthenticatedPrincipal(row.principalId(), row.customerId());
    }

    private record CredentialRow(UUID principalId, String customerId, String passwordHash) {}
}
