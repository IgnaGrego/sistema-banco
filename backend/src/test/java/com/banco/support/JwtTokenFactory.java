package com.banco.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Emisión de tokens JWT para tests de integración (ADR-004 §8.4). Vive en
 * scope de test: la producción solo valida (JwtService). Usa el MISMO secret
 * de application-test.yml y el MISMO contrato de claims que JwtService espera:
 *
 * <pre>
 * sub:        rol ("ADMIN" | "CLIENTE")   // provisional hasta SPEC-003
 * role:       "ADMIN" | "CLIENTE"
 * clienteId:  solo en tokens CLIENTE (Long)
 * iat, exp:   exp = now + banco.security.jwt-expiration-minutes
 * </pre>
 */
public class JwtTokenFactory {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtTokenFactory(String secret, long expirationMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String tokenAdmin() {
        return token("ADMIN", null);
    }

    public String tokenCliente(Long clienteId) {
        return token("CLIENTE", clienteId);
    }

    private String token(String rol, Long clienteId) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(rol)
                .claim("role", rol)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey);
        if (clienteId != null) {
            // Valor Long: evita la conversión numérica ambigua (Integer/Long)
            // al leer el claim en JwtService.
            builder.claim("clienteId", clienteId);
        }
        return builder.compact();
    }
}
