package com.kirafintech.ledger.web;

import com.kirafintech.ledger.domain.Route;
import com.kirafintech.ledger.repository.RouteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RouteRepository routeRepo;

    public RouteController(RouteRepository routeRepo) {
        this.routeRepo = routeRepo;
    }

    @GetMapping
    public ResponseEntity<List<Route>> listRoutes() {
        List<Route> routes = routeRepo.findAll();
        log.debug("listRoutes count={}", routes.size());
        return ResponseEntity.ok(routes);
    }
}
