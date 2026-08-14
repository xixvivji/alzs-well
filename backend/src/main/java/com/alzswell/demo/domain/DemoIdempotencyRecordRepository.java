package com.alzswell.demo.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoIdempotencyRecordRepository extends JpaRepository<DemoIdempotencyRecord, UUID> {

    Optional<DemoIdempotencyRecord> findByOperationKeyAndIdempotencyKey(
            String operationKey,
            String idempotencyKey
    );
}
