package com.alzswell.demo.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {

    long countByExpiresAtAfter(OffsetDateTime now);
}
