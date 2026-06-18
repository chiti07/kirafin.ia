package com.kirafintech.ledger.provider;

public interface FiatRailProvider {

    PaymentResult initiatePayment(PaymentParams params);

    PaymentResult getPaymentStatus(String providerRef);

    void cancelPayment(String providerRef);

    String getProviderKey();
}
