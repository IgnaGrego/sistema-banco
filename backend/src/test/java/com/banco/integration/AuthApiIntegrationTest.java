package com.banco.integration;

import com.banco.support.JwtTokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints /api/v1/auth (SPEC-003) con Testcontainers + MockMvc: un método por
 * criterio de aceptación (AC-001..AC-020 de la spec). Usa registro+login
 * REALES para los flujos de autenticación y el factory para crear clientes con
 * token ADMIN (helper) y para los casos negativos de autorización.
 *
 * Los usernames son ÚNICOS por método (el contenedor Postgres es por clase):
 * riesgo documentado en docs/architecture/SPEC-003.md §12.
 *
 * {@code @Testcontainers(disabledWithoutDocker = true)} también aquí, de forma
 * defensiva: la herencia no debe causar fallo duro si no hay Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuthApiIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${banco.security.jwt-secret}")
    private String jwtSecret;

    @TestConfiguration
    public static class TokenConfig {

        @Bean
        JwtTokenFactory jwtTokenFactory(
                @Value("${banco.security.jwt-secret}") String secret,
                @Value("${banco.security.jwt-expiration-minutes:60}") long expirationMinutes) {
            return new JwtTokenFactory(secret, expirationMinutes);
        }
    }

    // --- Helpers ---

    private String bodyRegistro(String username, String password, String rol, Long clienteId) {
        return """
                {"username":"%s","password":"%s","rol":"%s","clienteId":%s}
                """.formatted(username, password, rol,
                clienteId == null ? "null" : clienteId.toString());
    }

    private String bodyLogin(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private String bodyCliente(String nombre, String apellido, String dni, String email) {
        return """
                {"nombre":"%s","apellido":"%s","dni":"%s","email":"%s","telefono":null}
                """.formatted(nombre, apellido, dni, email);
    }

    private long crearClienteAdmin(String nombre, String apellido, String dni, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente(nombre, apellido, dni, email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void register(String username, String password, String rol, Long clienteId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro(username, password, rol, clienteId)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLogin(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private Claims decodificarToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    // --- Registro (AC-001..AC-007) ---

    @Test
    void AC001_registroCLIENTEConClienteExistenteResponde201SinPassword() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000001", "ac001-auth@example.com");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro("ac001-cliente", "password123", "CLIENTE", clienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.username").value("ac001-cliente"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                // BR-001 / AC-007: la respuesta NUNCA expone la password ni su hash.
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void AC002_registroADMINSinClienteIdResponde201() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro("ac002-admin", "password123", "ADMIN", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ac002-admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void AC003_passwordDe7CaracteresResponde400CampoPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro("ac003-cliente", "1234567", "CLIENTE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("password"));
    }

    @Test
    void AC004_usernameDuplicadoResponde409CampoUsername() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000004", "ac004-auth@example.com");
        register("ac004-repetido", "password123", "CLIENTE", clienteId);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro("ac004-repetido", "password123", "CLIENTE", clienteId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICTO_UNICIDAD"))
                .andExpect(jsonPath("$.details[0].campo").value("username"));
    }

    @Test
    void AC005_clienteSinClienteIdResponde400CampoClienteId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro("ac005-cliente", "password123", "CLIENTE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("clienteId"));
    }

    @Test
    void AC006_clienteConClienteIdInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRegistro("ac006-cliente", "password123", "CLIENTE", 999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENTE_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.message").value("Cliente no encontrado"));
    }

    // --- Login (AC-008..AC-011) ---

    @Test
    void AC008_loginCorrectoDevuelveJwtConClaimsSubRoleYClienteId() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000008", "ac008-auth@example.com");
        register("ac008-cliente", "password123", "CLIENTE", clienteId);

        String token = login("ac008-cliente", "password123");

        // FR-002 / AC-008: sub == username, role == CLIENTE, clienteId == el vinculado.
        Claims claims = decodificarToken(token);
        assertEquals("ac008-cliente", claims.getSubject());
        assertEquals("CLIENTE", claims.get("role", String.class));
        assertEquals(clienteId, ((Number) claims.get("clienteId")).longValue());
    }

    @Test
    void AC009_passwordIncorrectaResponde401NoAutenticado() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000009", "ac009-auth@example.com");
        register("ac009-cliente", "password123", "CLIENTE", clienteId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLogin("ac009-cliente", "incorrecta1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void AC010_usernameInexistenteResponde401ConMismoBodyQuePasswordIncorrecta() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000010", "ac010-auth@example.com");
        register("ac010-cliente", "password123", "CLIENTE", clienteId);

        // A-004 / ERR-001: respuesta IDÉNTICA para password incorrecta y username
        // inexistente (no se revela qué dato falló — no enumeración de usuarios).
        String bodyPasswordIncorrecta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLogin("ac010-cliente", "incorrecta1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andReturn().getResponse().getContentAsString();

        String bodyUsuarioInexistente = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLogin("ac010-ghost", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(bodyPasswordIncorrecta, bodyUsuarioInexistente);
    }

    // --- Autorización en endpoints de clientes (AC-012..AC-017) ---

    @Test
    void AC012_getClienteSinTokenResponde401ConEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clientes/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.message").value("Token ausente o inválido"));
    }

    @Test
    void AC013_getClienteConTokenMalformadoResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/clientes/1")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
    }

    @Test
    void AC014_clienteRegistradoYLogueadoConsultaSuPropioClienteResponde200() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000014", "ac014-auth@example.com");
        register("ac014-cliente", "password123", "CLIENTE", clienteId);
        String token = login("ac014-cliente", "password123");

        mockMvc.perform(get("/api/v1/clientes/{id}", clienteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clienteId));
    }

    @Test
    void AC015_clienteConsultandoClienteAjenoResponde403() throws Exception {
        long propio = crearClienteAdmin("Ana", "Lopez", "30000015", "ac015-auth@example.com");
        long ajeno = crearClienteAdmin("Bruno", "Diaz", "30000016", "ac015b-auth@example.com");
        register("ac015-cliente", "password123", "CLIENTE", propio);
        String token = login("ac015-cliente", "password123");

        // BR-004 / ERR-003: el CLIENTE solo accede a su Cliente vinculado.
        mockMvc.perform(get("/api/v1/clientes/{id}", ajeno)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC016_clienteSobrePostClientesResponde403() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000017", "ac016-auth@example.com");
        register("ac016-cliente", "password123", "CLIENTE", clienteId);
        String token = login("ac016-cliente", "password123");

        // Solo ADMIN crea clientes (SPEC-001 §9): CLIENTE → 403.
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Otro", "Cliente", "30000018", "ac016b-auth@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC017_adminRegistradoYLogueadoConsultaYCreaClientes() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000019", "ac017-auth@example.com");
        register("ac017-admin", "password123", "ADMIN", null);
        String token = login("ac017-admin", "password123");

        mockMvc.perform(get("/api/v1/clientes/{id}", clienteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clienteId));

        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Nuevo", "Admin", "30000020", "ac017b-auth@example.com")))
                .andExpect(status().isCreated());
    }

    // --- Persistencia real (AC-019) ---

    @Test
    void AC019_usuarioRegistradoPuedeLoguearseEnUnRequestPosterior() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "30000021", "ac019-auth@example.com");
        register("ac019-cliente", "password123", "CLIENTE", clienteId);

        // Login en un request posterior: la persistencia es real (Postgres).
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyLogin("ac019-cliente", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
