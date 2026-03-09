package com.azizsattarov.corebanking.auth.dto;

import com.azizsattarov.corebanking.auth.User;
import com.azizsattarov.corebanking.auth.UserRepository;
import com.azizsattarov.corebanking.auth.UserRole;

public record RegisterRequest(String username, String password, UserRole role) {}