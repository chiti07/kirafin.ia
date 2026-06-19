package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.PayoutJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface PayoutJobRepository extends JpaRepository<PayoutJob, UUID> {

    @Query(value = """
        SELECT * FROM payout_jobs
        WHERE status = 'pending' AND next_attempt_at <= NOW()
        ORDER BY next_attempt_at
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<PayoutJob> claimPendingJobs(@Param("batchSize") int batchSize);

    // Reconciliation: settled-with-no-entry — job COMPLETED but transfer still PENDING
    @Query(value = """
        SELECT pj.* FROM payout_jobs pj
        JOIN transfers t ON t.id = pj.transfer_id
        WHERE pj.status = 'completed' AND t.status = 'pending'
        ORDER BY pj.updated_at
        """, nativeQuery = true)
    List<PayoutJob> findSettledWithPendingTransfer();

    // Alerting: recently failed jobs
    @Query(value = """
        SELECT * FROM payout_jobs
        WHERE status = 'failed' AND updated_at > :since
        ORDER BY updated_at DESC
        """, nativeQuery = true)
    List<PayoutJob> findFailedSince(@Param("since") Instant since);

    // Dashboard: count by status
    @Query(value = "SELECT COUNT(*) FROM payout_jobs WHERE status = :status", nativeQuery = true)
    long countByStatus(@Param("status") String status);
}
