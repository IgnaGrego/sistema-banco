package com.banco.infrastructure.security;

import com.banco.support.JwtTokenFactory;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validación de JWT (JwtService, HS256, ADR-004): firma correcta con claims
 * ADMIN/CLIENTE, rechazo por firma distinta y por expiración. Test plano
 * (JUnit puro, sin contexto Spring): construye JwtService con el MISMO secret
 * de application-test.yml y emite tokens con {@link JwtTokenFactory} (mismo
 * contrato de claims que producción).
 */
class JwtServiceTest {

    /** Mismo valor que banco.security.jwt-secret en application-test.yml. */
    private static final String SECRET = "test-only-secret-clave-hs256-de-32-bytes-minimo";

    /** Otro secret (>= 32 bytes) para tokens con firma inválida. */
    private static final String OTRO_SECRET = "otro-secret-distinto-tambien-de-32-bytes-minimo";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void tokenAdminValidoSeParseaConRolADMINYClienteIdNull() {
        String token = new JwtTokenFactory(SECRET, 60).tokenAdmin();

        AuthenticatedUser usuario = jwtService.validar(token);

        assertEquals("ADMIN", usuario.rol());
        assertNull(usuario.clienteId());
    }

    @Test
    void tokenClienteValidoSeParseaConClienteIdLong() {
        // Valor > Integer.MAX_VALUE para verificar el round-trip del claim
        // como Long (JwtService convierte Number defensivamente).
        Long clienteId = 1_234_567_890_123L;
        String token = new JwtTokenFactory(SECRET, 60).tokenCliente(clienteId);

        AuthenticatedUser usuario = jwtService.validar(token);

        assertEquals("CLIENTE", usuario.rol());
        assertEquals(clienteId, usuario.clienteId());
    }

    @Test
    void tokenFirmadoConOtroSecretEsRechazado() {
        String token = firmarCon(OTRO_SECRET, Instant.now().plus(1, ChronoUnit.HOURS));

        assertThrows(JwtException.class, () -> jwtService.validar(token));
    }

    @Test
    void tokenExpiradoEsRechazado() {
        // Firma correcta pero exp en el pasado.
        String token = firmarCon(SECRET, Instant.now().minus(1, ChronoUnit.HOURS));

        assertThrows(JwtException.class, () -> jwtService.validar(token));
    }

    private String firmarCon(String secret, Instant exp) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("ADMIN")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }
}
