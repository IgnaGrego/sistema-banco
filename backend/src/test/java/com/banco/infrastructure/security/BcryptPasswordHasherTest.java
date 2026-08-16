package com.banco.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BcryptPasswordHasher con BCrypt real (AC-007, AC-011): el hash nunca es igual
 * a la password en claro (BR-001), longitud 60, matches verifica la original,
 * rechaza otra y dos hashes del mismo valor difieren (salt). Test plano (JUnit
 * puro, sin contexto Spring).
 */
class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher =
            new BcryptPasswordHasher(new BCryptPasswordEncoder());

    @Test
    void hashNoEsIgualALaPasswordEnClaroYTieneLongitud60() {
        String password = "mi-password-123";

        String hash = hasher.hash(password);

        assertNotEquals(password, hash); // nunca en claro (BR-001, AC-007)
        assertEquals(60, hash.length()); // VARCHAR(60) de la migración V2
    }

    @Test
    void matchesVerificaLaPasswordOriginalYRechazaOtra() {
        String password = "mi-password-123";
        String hash = hasher.hash(password);

        assertTrue(hasher.matches(password, hash));
        assertFalse(hasher.matches("otra-password", hash));
        // NOTA: no se testea matches(null, hash): BCryptPasswordEncoder lanza
        // IllegalArgumentException con rawPassword null. El use case de login
        // nunca lo invoca con null (guard defensivo → 401 antes de matches).
    }

    @Test
    void dosHashDelMismoValorDifierenPorElSalt() {
        assertNotEquals(hasher.hash("mi-password-123"), hasher.hash("mi-password-123"));
    }
}
