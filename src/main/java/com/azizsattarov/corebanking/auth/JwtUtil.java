package com.azizsattarov.corebanking.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    // Create secret key from secret string
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())     // stores username inside token (in payload)
                .claim("role", userDetails.getAuthorities().iterator().next().getAuthority())       // stores "ROLE_ADMIN" inside token (in payload)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())          // create a signature
                .compact();         //combine everything into a string
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername())
                && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    // Decodes the token and returns all its data (claims)
    // Throws exception if token is tampered with or invalid
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())        //verify signature
                .build()
                .parseSignedClaims(token)       // decode the token
                .getPayload();                  // get data inside
    }
}