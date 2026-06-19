package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE Transfer t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") TransferStatus status);

    // Alerting: stuck pending transfers older than threshold
    @Query("SELECT t FROM Transfer t WHERE t.status = 'PENDING' AND t.createdAt < :threshold")
    List<Transfer> findStuckPending(@Param("threshold") Instant threshold);

    // Dashboard: recent transfers
    @Query("SELECT t FROM Transfer t ORDER BY t.createdAt DESC")
    List<Transfer> findRecent(org.springframework.data.domain.Pageable pageable);

    // Dashboard: count by status
    @Query("SELECT COUNT(t) FROM Transfer t WHERE t.status = :status")
    long countByStatus(@Param("status") TransferStatus status);
}
