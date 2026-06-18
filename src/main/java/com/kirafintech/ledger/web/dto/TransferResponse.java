package com.kirafintech.ledger.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.kirafintech.ledger.domain.Transfer;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String idempotencyKey,
        String direction,
        String type,
        String status,
        UUID accountId,
        @JsonSerialize(using = ToStringSerializer.class) long amountMinorUnits,
        String currency,
        String chain,
        Instant createdAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(t.getId(), t.getIdempotencyKey(),
                t.getDirection().name(), t.getType().name(), t.getStatus().name(),
                t.getAccountId(), t.getAmountMinorUnits(), t.getCurrency(),
                t.getChain(), t.getCreatedAt());
    }
}
