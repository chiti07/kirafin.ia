package com.kirafintech.ledger.repository;

import com.kirafintech.ledger.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    List<Route> findByAccountIdAndActiveTrue(UUID accountId);
}
