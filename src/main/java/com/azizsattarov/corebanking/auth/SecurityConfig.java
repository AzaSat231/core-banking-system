package com.azizsattarov.corebanking.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ServiceTokenAuthFilter serviceTokenAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          ServiceTokenAuthFilter serviceTokenAuthFilter,
                          RateLimitFilter rateLimitFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.serviceTokenAuthFilter = serviceTokenAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).hasRole("ADMIN")

                        // ── ATM public endpoints ───────────────────────────────
                        // Card login and resolve — called by middleware before a session exists
                        .requestMatchers(HttpMethod.POST, "/atm/login").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/atm/resolve-card").permitAll()

                        // ── ATM self-service card + PIN setup ─────────────────
                        // Both gated by ROLE_SERVICE (middleware service token).
                        // The middleware rate-limits and validates these before forwarding.
                        // Customers never call Core Banking directly — only middleware does.
                        .requestMatchers(HttpMethod.POST, "/atm/create-card-for-account").hasRole("SERVICE")
                        .requestMatchers(HttpMethod.POST, "/atm/prepare-own-pin").hasRole("SERVICE")
                        .requestMatchers(HttpMethod.POST, "/atm/set-own-pin").hasRole("SERVICE")

                        // Customer PIN reset after admin unlock — middleware service token
                        .requestMatchers(HttpMethod.POST, "/atm/reset-pin").hasRole("SERVICE")

                        // Set PIN by admin at branch — requires admin JWT
                        .requestMatchers(HttpMethod.POST, "/atm/set-pin").hasRole("ADMIN")

                        // ── Customers ──────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/customers/{customerId}/accounts").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST,   "/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/customers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/customers/**").hasRole("ADMIN")

                        // ── Accounts ───────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/customers/*/accounts").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST,   "/customers/*/accounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,  "/customers/*/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/customers/*/accounts/**").hasRole("ADMIN")

                        // ── Transactions ───────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/accounts/*/transactions").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/accounts/**").hasAnyRole("ADMIN", "USER")

                        // ── Cards ──────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,    "/accounts/*/cards").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST,   "/accounts/*/cards").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/accounts/*/cards/*/send-email").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,  "/accounts/*/cards/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/accounts/*/cards/**").hasRole("ADMIN")

                        // ── Reconciliation worker ──────────────────────────────
                        .requestMatchers("/admin/**").hasRole("SERVICE")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(serviceTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}