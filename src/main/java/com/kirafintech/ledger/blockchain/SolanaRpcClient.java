package com.kirafintech.ledger.blockchain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Thin HTTP client for Solana JSON-RPC.
 * Uses raw WebClient to avoid library Java-version compatibility issues (ADR-015).
 */
@Component
public class SolanaRpcClient {

    private static final Logger log = LoggerFactory.getLogger(SolanaRpcClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public SolanaRpcClient(@Value("${app.solana.rpc-url}") String rpcUrl,
                           WebClient.Builder webClientBuilder,
                           ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(rpcUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<SignatureInfo> getSignaturesForAddress(String address, String before) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("limit", 50);
        options.put("commitment", "confirmed");
        if (before != null && !before.isBlank()) {
            options.put("before", before);
        }

        Map<String, Object> request = buildRequest("getSignaturesForAddress",
                List.of(address, options));
        Map<String, Object> response = callRpc(request);
        if (response == null) return List.of();

        List<?> result = (List<?>) response.get("result");
        if (result == null) return List.of();

        List<SignatureInfo> sigs = new ArrayList<>();
        for (Object item : result) {
            try {
                Map<?, ?> m = (Map<?, ?>) item;
                String sig = (String) m.get("signature");
                long slot = ((Number) m.get("slot")).longValue();
                Object err = m.get("err");
                sigs.add(new SignatureInfo(sig, slot, err != null));
            } catch (Exception e) {
                log.debug("Failed to parse signature info: {}", e.getMessage());
            }
        }
        return sigs;
    }

    public TransactionDetail getTransaction(String signature) {
        Map<String, Object> options = Map.of(
                "encoding", "jsonParsed",
                "commitment", "confirmed",
                "maxSupportedTransactionVersion", 0
        );
        Map<String, Object> request = buildRequest("getTransaction", List.of(signature, options));
        Map<String, Object> response = callRpc(request);
        if (response == null || response.get("result") == null) return null;

        return new TransactionDetail(signature, response.get("result"));
    }

    public long getCurrentSlot() {
        Map<String, Object> request = buildRequest("getSlot",
                List.of(Map.of("commitment", "confirmed")));
        Map<String, Object> response = callRpc(request);
        if (response == null || response.get("result") == null) return 0L;
        return ((Number) response.get("result")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callRpc(Map<String, Object> request) {
        try {
            String responseBody = webClient.post()
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (responseBody == null) return null;
            return objectMapper.readValue(responseBody, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Solana RPC call failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequest(String method, List<Object> params) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", method,
                "params", params
        );
    }

    public record SignatureInfo(String signature, long slot, boolean hasError) {}

    public record TransactionDetail(String signature, Object raw) {}
}
