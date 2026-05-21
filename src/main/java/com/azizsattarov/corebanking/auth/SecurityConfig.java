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

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          ServiceTokenAuthFilter serviceTokenAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.serviceTokenAuthFilter = serviceTokenAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // ATM login is public — called by middleware only
                        .requestMatchers(HttpMethod.POST, "/atm/login").permitAll()
                        // Customer PIN reset after admin unlock — middleware service token only
                        .requestMatchers(HttpMethod.POST, "/atm/reset-pin").hasRole("SERVICE")
                        // Set PIN requires admin JWT
                        .requestMatchers(HttpMethod.POST, "/atm/set-pin").hasRole("ADMIN")

                        // Customers
                        .requestMatchers(HttpMethod.GET, "/customers/**").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/customers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/customers/**").hasRole("ADMIN")

                        // Accounts
                        .requestMatchers(HttpMethod.GET, "/customers/*/accounts").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/customers/*/accounts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/customers/*/accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/customers/*/accounts/**").hasRole("ADMIN")

                        // Transactions
                        .requestMatchers(HttpMethod.GET, "/accounts/*/transactions").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/accounts/**").hasAnyRole("ADMIN", "USER")

                        // Reconciliation worker — gated by ServiceTokenAuthFilter
                        .requestMatchers("/admin/**").hasRole("SERVICE")

                        .anyRequest().authenticated()
                )
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