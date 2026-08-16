package com.banco.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validación de tokens JWT HS256 (ADR-004): verifica firma y expiración y
 * extrae los claims {@code role} y {@code clienteId} (opcional, solo tokens
 * CLIENTE). NO emite tokens: la emisión vive en scope de test (JwtTokenFactory)
 * y en SPEC-003 (Sprint 2).
 *
 * Contrato de claims (Sprint 1): sub = rol (provisional), role, clienteId
 * (solo CLIENTE), iat, exp.
 */
@Component
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(@Value("${banco.security.jwt-secret}") String secret) {
        // jjwt (HS256) exige una clave de al menos 32 bytes.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedUser validar(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String rol = claims.get("role", String.class);
        Long clienteId = null;
        Object clienteIdClaim = claims.get("clienteId");
        if (clienteIdClaim instanceof Number number) {
            // jjwt puede devolver Integer o Long según el valor; se convierte
            // defensivamente para evitar RequiredTypeException.
            clienteId = number.longValue();
        }
        return new AuthenticatedUser(clienteId, rol);
    }
}
