package com.kirafintech.ledger.web.dto;

import com.kirafintech.ledger.domain.Account;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String clientName,
        boolean omnibus,
        String currency,
        Instant createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getClientName(), a.isOmnibus(),
                a.getCurrency(), a.getCreatedAt());
    }
}
