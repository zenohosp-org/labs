package com.labs.server.repository;

import com.labs.server.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.PK> {

    Optional<IdempotencyKey> findByHospitalIdAndIdempotencyKey(UUID hospitalId, String idempotencyKey);

    /** Prune helper. Not auto-invoked today; a cron can call this later. */
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
