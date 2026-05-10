package com.azizsattarov.corebanking.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates the in-middleware reconciliation worker for /admin/** endpoints.
 *
 * The worker sends `X-Service-Token: <shared secret>`. If it matches
 * `app.middleware.service-token`, the request is granted ROLE_SERVICE. No JWT
 * is involved, so a leaked user JWT cannot reach admin endpoints.
 *
 * If the configured token is empty/blank, all admin requests are rejected
 * (defense-in-default). Set the value in application.properties or via the
 * MIDDLEWARE_SERVICE_TOKEN environment variable in production.
 */
@Component
public class ServiceTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    private final String configuredToken;

    public ServiceTokenAuthFilter(
            @Value("${app.middleware.service-token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/admin/")) {
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
