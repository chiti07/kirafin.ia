package com.kirafintech.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kirafintech.ledger.domain.Route;
import com.kirafintech.ledger.domain.Transfer;
import com.kirafintech.ledger.domain.enums.TransferType;
import com.kirafintech.ledger.repository.RouteRepository;
import com.kirafintech.ledger.repository.TransferRepository;
import com.kirafintech.ledger.application.port.in.LedgerPort;
import com.kirafintech.ledger.application.port.in.LedgerPort.PostOutboundDebitCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RouteEngine {

    private static final Logger log = LoggerFactory.getLogger(RouteEngine.class);

    private final RouteRepository routeRepo;
    private final TransferRepository transferRepo;
    private final LedgerPort ledger;
    private final ObjectMapper objectMapper;

    public RouteEngine(RouteRepository routeRepo, TransferRepository transferRepo,
                       LedgerPort ledger, ObjectMapper objectMapper) {
        this.routeRepo = routeRepo;
        this.transferRepo = transferRepo;
        this.ledger = ledger;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates all active routes for an account and fires eligible ones.
     * Idempotency key per route: accountId:routeId:triggerTransferId
     * Writes debit entries + payout_job in the same transaction as the caller.
     */
    @Transactional
    public List<Transfer> evaluateAndFire(UUID accountId, UUID triggerTransferId) {
        List<Route> activeRoutes = routeRepo.findByAccountIdAndActiveTrue(accountId);
        List<Transfer> fired = new ArrayList<>();

        for (Route route : activeRoutes) {
            String routeKey = accountId + ":" + route.getId() + ":" + triggerTransferId;

            if (transferRepo.existsByIdempotencyKey(routeKey)) {
                log.debug("Route {} already fired for trigger {}, skipping", route.getId(), triggerTransferId);
                continue;
            }

            String metadata = toJson(Map.of(
                    "route_id", route.getId().toString(),
                    "trigger_transfer_id", triggerTransferId.toString(),
                    "destination_ref", route.getDestinationRef()
            ));

            PostOutboundDebitCommand cmd = new PostOutboundDebitCommand(
                    routeKey,
                    accountId,
                    SystemAccounts.KIRA_LIQUIDITY_POOL,
                    route.getAmountValue(),
                    route.getCurrency(),
                    TransferType.FIAT,
                    route.getDestinationRef(),
                    route.getDestinationType(),
                    route.getProvider() != null ? route.getProvider() : "simple_rail",
                    0L,
                    List.of(),
                    metadata
            );

            try {
                Transfer outbound = ledger.postOutboundDebit(cmd);
                fired.add(outbound);
                log.info("Route fired: route={} amount={} provider={} transfer={}",
                        route.getName(), route.getAmountValue(), route.getProvider(), outbound.getId());
            } catch (Exception e) {
                log.warn("Route {} failed to fire: {}", route.getName(), e.getMessage());
            }
        }

        return fired;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
