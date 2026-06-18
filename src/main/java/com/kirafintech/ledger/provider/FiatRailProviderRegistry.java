package com.kirafintech.ledger.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FiatRailProviderRegistry {

    private final Map<String, FiatRailProvider> providers;

    public FiatRailProviderRegistry(List<FiatRailProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(FiatRailProvider::getProviderKey, Function.identity()));
    }

    public FiatRailProvider get(String providerKey) {
        FiatRailProvider provider = providers.get(providerKey);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerKey);
        }
        return provider;
    }
}
