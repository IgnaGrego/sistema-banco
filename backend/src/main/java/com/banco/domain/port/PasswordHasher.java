package com.banco.domain.port;

/**
 * Abstracción del hashing de passwords (BCrypt, ADR-003/ADR-005). Aísla a la
 * capa de aplicación de {@code org.springframework.security.crypto...}
 * (regla ArchUnit "application depende solo de domain"). Lo implementa
 * {@code BcryptPasswordHasher} en infrastructure.
 *
 * La password en claro nunca se persiste ni se expone (BR-001); el hashing
 * nunca devuelve la password original (AC-007).
 */
public interface PasswordHasher {

    String hash(String password);

    boolean matches(String rawPassword, String passwordHash);
}
