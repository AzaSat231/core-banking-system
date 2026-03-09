package com.azizsattarov.corebanking.auth;

import com.azizsattarov.corebanking.auth.dto.AuthResponse;
import com.azizsattarov.corebanking.auth.dto.LoginRequest;
import com.azizsattarov.corebanking.auth.dto.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final AppUserDetailsService userDetailsService;
    private final UserRepository appUserRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authManager,
                          AppUserDetailsService userDetailsService,
                          UserRepository appUserRepository,
                          JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder) {
        this.authManager = authManager;
        this.userDetailsService = userDetailsService;
        this.appUserRepository = appUserRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (appUserRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.role()   // ← was request.role().getRole(), UserRole has no getRole()
        );
        appUserRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.status(201)
                .body(new AuthResponse(token, user.getUsername(), user.getRole().name()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtUtil.generateToken(userDetails);
        User user = appUserRepository.findByUsername(request.username()).get();

        return ResponseEntity.ok(
                new AuthResponse(token, user.getUsername(), user.getRole().name())
        );
    }
}
