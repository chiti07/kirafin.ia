package com.kirafintech.ledger.service;

import java.util.UUID;

public final class SystemAccounts {

    public static final UUID KIRA_FEE_ACCOUNT    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID KIRA_LIQUIDITY_POOL = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID CRYPTO_SUSPENSE     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID NORTHWIND_OMNIBUS   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID NORTHWIND_MAIN      = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private SystemAccounts() {}
}
