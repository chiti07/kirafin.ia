package com.kirafintech.ledger.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String clientName,
        boolean omnibus,
        String parentAccountId
) {}
