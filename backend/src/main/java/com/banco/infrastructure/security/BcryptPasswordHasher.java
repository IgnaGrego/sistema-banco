package com.banco.infrastructure.security;

import com.banco.domain.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementación del puerto {@link PasswordHasher} con BCrypt (ADR-003,
 * ADR-005): envuelve el bean {@link PasswordEncoder} (BCryptPasswordEncoder,
 * provisto por spring-boot-starter-security — sin dependencias nuevas).
 *
 * Mantiene a {@code application} libre de Spring: los use cases reciben el
 * puerto de dominio, nunca esta clase (regla ArchUnit).
 */
@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BcryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
