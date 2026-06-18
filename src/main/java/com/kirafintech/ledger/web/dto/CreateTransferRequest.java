package com.kirafintech.ledger.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreateTransferRequest(
        @NotBlank String idempotencyKey,
        @NotNull UUID accountId,
        @NotBlank String type,
        @Positive long amountMinorUnits,
        @NotBlank String currency,
        String chain,
        boolean confirmed
) {}
