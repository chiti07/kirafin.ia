package com.kirafintech.ledger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final String expectedKey;

    public ApiKeyAuthFilter(@Value("${app.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String path = req.getServletPath();
        if (path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus")) {
            chain.doFilter(req, res);
            return;
        }

        String key = req.getHeader(HEADER);
        if (expectedKey.equals(key)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    key, null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(req, res);
        } else {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus");
    }
}
