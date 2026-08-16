package com.banco.infrastructure.security;

import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import com.banco.domain.port.TokenEmisor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Emisión y validación de tokens JWT HS256 (ADR-005): {@link #emitir} firma
 * tokens para producción (implementa el puerto de dominio {@link TokenEmisor})
 * y {@link #validar} verifica firma y expiración y extrae los claims {@code role}
 * y {@code clienteId} (opcional, solo tokens CLIENTE) — sin cambios (ADR-004).
 *
 * Contrato de claims (SPEC-003 §6.3):
 *
 * <pre>
 * sub:        username              // antes (Sprint 1): rol (provisional)
 * role:       "ADMIN" | "CLIENTE"
 * clienteId:  solo en tokens CLIENTE (Long)
 * iat, exp:   exp = now + banco.security.jwt-expiration-minutes (default 60)
 * </pre>
 *
 * {@code validar} nunca lee {@code sub}: la validación, el filtro y la
 * resolución de rol/clienteId son agnósticos al cambio de contrato (ADR-004 §5).
 */
@Component
public class JwtService implements TokenEmisor {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(@Value("${banco.security.jwt-secret}") String secret,
                      @Value("${banco.security.jwt-expiration-minutes:60}") long expirationMinutes) {
        // jjwt (HS256) exige una clave de al menos 32 bytes.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    @Override
    public String emitir(Usuario usuario) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(usuario.getUsername())
                .claim("role", usuario.getRol().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey);
        if (usuario.getRol() == Rol.CLIENTE && usuario.getClienteId() != null) {
            // Valor Long: evita la conversión numérica ambigua (Integer/Long)
            // al leer el claim en validar.
            builder.claim("clienteId", usuario.getClienteId());
        }
        return builder.compact();
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
