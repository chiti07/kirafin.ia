package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE Transfer t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") TransferStatus status);
}
