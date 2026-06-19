package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.Entry;
import com.kirafintech.ledger.domain.enums.EntryDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.direction = :credit THEN e.amount ELSE -e.amount END), 0)
        FROM Entry e
        WHERE e.accountId = :accountId AND e.currency = :currency AND e.confirmed = true
        """)
    long getAvailableBalance(
            @Param("accountId") UUID accountId,
            @Param("currency") String currency,
            @Param("credit") EntryDirection credit);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.direction = :credit THEN e.amount ELSE -e.amount END), 0)
        FROM Entry e
        WHERE e.accountId = :accountId AND e.currency = :currency
        """)
    long getPendingBalance(
            @Param("accountId") UUID accountId,
            @Param("currency") String currency,
            @Param("credit") EntryDirection credit);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN e.direction = :credit THEN e.amount ELSE -e.amount END), 0)
        FROM Entry e
        WHERE e.currency = 'USD'
        """)
    long getGlobalDoubleEntryNet(@Param("credit") EntryDirection credit);

    // The ONLY UPDATE ever permitted on the entries table (ADR-002)
    @Modifying
    @Query("UPDATE Entry e SET e.confirmed = true WHERE e.transferId = :transferId AND e.confirmed = false")
    int confirmEntriesForTransfer(@Param("transferId") UUID transferId);

    // Reconciliation: entries still unconfirmed after the given threshold (entry-never-confirmed)
    @Query(value = """
        SELECT * FROM entries
        WHERE confirmed = false
          AND created_at < NOW() - INTERVAL '1 minute' * :thresholdMinutes
        ORDER BY created_at
        """, nativeQuery = true)
    List<Entry> findStaleUnconfirmedEntries(@Param("thresholdMinutes") int thresholdMinutes);
}
