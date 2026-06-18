package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.PayoutJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PayoutJobRepository extends JpaRepository<PayoutJob, UUID> {

    @Query(value = """
        SELECT * FROM payout_jobs
        WHERE status = 'pending' AND next_attempt_at <= NOW()
        ORDER BY next_attempt_at
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<PayoutJob> claimPendingJobs(@Param("batchSize") int batchSize);
}
