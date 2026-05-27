package com.azizsattarov.corebanking.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates the middleware / reconciliation worker for service-only endpoints.
 *
 * The caller sends `X-Service-Token: <shared secret>`. If it matches
 * `app.middleware.service-token`, the request is granted ROLE_SERVICE. No JWT
 * is involved, so a leaked user JWT cannot reach these endpoints.
 *
 * Protected paths (POST only unless noted):
 *   /admin/**                       — blockchain reconciliation worker
 *   /atm/reset-pin                  — PIN reset after admin unlock
 *   /atm/create-card-for-account    — self-service card issuance (new)
 *   /atm/set-own-pin                — self-service initial PIN setup (new)
 *
 * If the configured token is empty/blank, all service requests are rejected
 * (defense-in-default). Set the value via the MIDDLEWARE_SERVICE_TOKEN
 * environment variable or keychain entry in production.
 */
@Component
@Order(1)
public class ServiceTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    private final String configuredToken;

    // Exact-match paths that require the service token (method-agnostic check
    // is fine here; method restrictions are enforced by SecurityConfig).
    private static final Set<String> SERVICE_PATHS = Set.of(
            "/atm/reset-pin",
            "/atm/create-card-for-account",
            "/atm/set-own-pin"
    );

    public ServiceTokenAuthFilter(
            @Value("${app.middleware.service-token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        boolean adminPath   = path != null && path.startsWith("/admin/");
        boolean servicePath = path != null && SERVICE_PATHS.contains(path)
                && "POST".equalsIgnoreCase(method);

        if (!adminPath && !servicePath) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (configuredToken.isEmpty() || provided == null
                || !constantTimeEquals(configuredToken, provided)) {
            chain.doFilter(request, response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "SERVICE_MIDDLEWARE",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}