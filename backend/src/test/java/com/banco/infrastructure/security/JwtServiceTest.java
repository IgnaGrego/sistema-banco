package com.banco.infrastructure.security;

import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import com.banco.support.JwtTokenFactory;
import io.jsonwebtoken.Claims;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtService (HS256, ADR-004/ADR-005): validación (firma correcta con claims
 * ADMIN/CLIENTE, rechazo por firma distinta y por expiración) y emisión
 * (round-trip emitir→validar con el contrato de claims de SPEC-003 §6.3:
 * sub = username, role, clienteId solo CLIENTE, exp = now + configuración).
 * Test plano (JUnit puro, sin contexto Spring): construye JwtService con el
 * MISMO secret de application-test.yml; para validar usa tokens de
 * {@link JwtTokenFactory} (mismo contrato de claims que producción).
 */
class JwtServiceTest {

    /** Mismo valor que banco.security.jwt-secret en application-test.yml. */
    private static final String SECRET = "test-only-secret-clave-hs256-de-32-bytes-minimo";

    /** Otro secret (>= 32 bytes) para tokens con firma inválida. */
    private static final String OTRO_SECRET = "otro-secret-distinto-tambien-de-32-bytes-minimo";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    // --- Validación (sin cambios de contrato para validar) ---

    @Test
    void tokenAdminValidoSeParseaConRolADMINYClienteIdNull() {
        String token = new JwtTokenFactory(SECRET, 60).tokenAdmin("admin-test");

        AuthenticatedUser usuario = jwtService.validar(token);

        assertEquals("ADMIN", usuario.rol());
        assertNull(usuario.clienteId());
    }

    @Test
    void tokenClienteValidoSeParseaConClienteIdLong() {
        // Valor > Integer.MAX_VALUE para verificar el round-trip del claim
        // como Long (JwtService convierte Number defensivamente).
        Long clienteId = 1_234_567_890_123L;
        String token = new JwtTokenFactory(SECRET, 60).tokenCliente("cliente-test", clienteId);

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

    // --- Emisión (SPEC-003 §6.3: sub = username, role, clienteId solo CLIENTE) ---

    @Test
    void emitirUsuarioCLIENTEGeneraTokenConSubRoleYClienteId() {
        Usuario usuario = new Usuario(1L, "cliente1", "hash-bcrypt", Rol.CLIENTE, 7L);

        String token = jwtService.emitir(usuario);

        Claims claims = parseClaims(token);
        assertEquals("cliente1", claims.getSubject());
        assertEquals("CLIENTE", claims.get("role", String.class));
        assertEquals(7L, claims.get("clienteId", Long.class));
        // Round-trip: validar resuelve rol/clienteId desde el token emitido.
        AuthenticatedUser autenticado = jwtService.validar(token);
        assertEquals("CLIENTE", autenticado.rol());
        assertEquals(7L, autenticado.clienteId());
    }

    @Test
    void emitirUsuarioADMINNoIncluyeClienteId() {
        Usuario usuario = new Usuario(2L, "admin", "hash-bcrypt", Rol.ADMIN, null);

        String token = jwtService.emitir(usuario);

        Claims claims = parseClaims(token);
        assertEquals("admin", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
        assertFalse(claims.containsKey("clienteId"));
        AuthenticatedUser autenticado = jwtService.validar(token);
        assertEquals("ADMIN", autenticado.rol());
        assertNull(autenticado.clienteId());
    }

    @Test
    void emitirCLIENTESinClienteIdNoIncluyeElClaim() {
        // Defensivo: un CLIENTE sin vínculo no lleva claim clienteId (A-003,
        // AF-002: sin vínculo no puede acceder a recursos de clientes).
        Usuario usuario = new Usuario(3L, "cliente-sin-vinculo", "hash-bcrypt", Rol.CLIENTE, null);

        String token = jwtService.emitir(usuario);

        assertFalse(parseClaims(token).containsKey("clienteId"));
        assertNull(jwtService.validar(token).clienteId());
    }

    @Test
    void expiracionEsNowMasLaConfiguracion() {
        long expirationMinutes = 60;
        JwtService service = new JwtService(SECRET, expirationMinutes);

        String token = service.emitir(new Usuario(4L, "u", "h", Rol.CLIENTE, 1L));

        Claims claims = parseClaims(token);
        long deltaMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(expirationMinutes * 60_000L, deltaMillis);
    }

    // --- Helpers ---

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String firmarCon(String secret, Instant exp) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }
}
