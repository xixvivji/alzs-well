package com.alzswell.demo.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {

    long countByExpiresAtAfter(OffsetDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from DemoSession session where session.sessionId = :sessionId")
    Optional<DemoSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);
}
