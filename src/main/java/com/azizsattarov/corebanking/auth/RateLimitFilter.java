package com.azizsattarov.corebanking.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/*
Rate Limit protects against one IP hammering the endpoint
(attacker can't bruteforce pin number through one IP address)


A bucket is a counter with a maximum capacity.
Think of it like a physical bucket that holds tokens.
You start with 10 tokens. Every login attempt takes one token out.
When the bucket is empty you cannot make any more attempts until it refills.

Start:    [■■■■■■■■■■]  10 tokens
Attempt 1: [■■■■■■■■■□]  9 tokens
Attempt 2: [■■■■■■■■□□]  8 tokens
...
Attempt 10:[□□□□□□□□□□]  0 tokens → next request gets 429
After 1min:[■■■■■■■■■■]  refills back to 10



Layer 1 — RateLimitFilter in Core Banking:

Limits to 10 attempts per minute per IP
At 10 attempts per minute, trying all 10,000 combinations would take 1,000 minutes = 16 hours minimum
Attacker gets 429 immediately with no database hit, no session, no nothing

Layer 2 — Progressive lockout in Middleware:

After 3 wrong PINs → account locked 15 minutes
After 6 wrong PINs → account locked 30 minutes
After 9 wrong PINs → account permanently locked, requires admin to unlock

 */

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if ("/atm/login".equals(uri) || "/atm/resolve-card".equals(uri)) {
            String ip = request.getRemoteAddr();
            Bucket bucket = buckets.get(ip, k -> newBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.getWriter().write("{\"error\":\"Too many requests\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}