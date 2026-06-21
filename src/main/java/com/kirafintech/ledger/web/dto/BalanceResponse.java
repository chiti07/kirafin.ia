package com.kirafintech.ledger.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record BalanceResponse(
        @JsonSerialize(using = ToStringSerializer.class) long availableCents,
        @JsonSerialize(using = ToStringSerializer.class) long pendingCents,
        @JsonSerialize(using = ToStringSerializer.class) long pendingUsdcMinorUnits,
        String currency
) {}
