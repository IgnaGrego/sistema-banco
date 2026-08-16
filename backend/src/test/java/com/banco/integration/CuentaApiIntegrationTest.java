package com.banco.integration;

import com.banco.infrastructure.adapter.web.ErrorResponse;
import com.banco.infrastructure.adapter.web.GlobalExceptionHandler;
import com.banco.support.JwtTokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints REST de cuentas con Testcontainers + MockMvc: un método por
 * criterio de aceptación (AC-001..AC-023) + verificación de persistencia y
 * constraints (AC-029). Patrón de {@code ClienteApiIntegrationTest}: la
 * {@code @TestConfiguration TokenConfig} anidada declara el bean
 * {@link JwtTokenFactory} con el secret de application-test.yml.
 *
 * <p>Usernames de token ÚNICOS por método y clientes con DNI/email únicos (el
 * contenedor Postgres es por clase — riesgo documentado en SPEC-002 §12).
 */
@Testcontainers(disabledWithoutDocker = true)
class CuentaApiIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

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

    private String bodyCuenta(Long clienteId, String tipo, String moneda) {
        return """
                {"clienteId":%s,"tipo":"%s","moneda":%s}
                """.formatted(clienteId, tipo, moneda == null ? "null" : "\"" + moneda + "\"");
    }

    private JsonNode crearCuentaAdmin(long clienteId, String tipo, String moneda) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(clienteId, tipo, moneda)))
                .andExpect(status().isCreated())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private static void assertSaldoCero(JsonNode json) {
        // El saldo se compara numéricamente (compareTo): tolerante a la escala
        // del BigDecimal serializado (0 vs 0.00 según el mapeo de la BD).
        assertEquals(0, json.get("saldo").decimalValue().compareTo(BigDecimal.ZERO),
                "El saldo debe ser 0");
    }

    private static void assertCbuDe22Digitos(JsonNode json) {
        assertTrue(json.get("cbu").asText().matches("^[0-9]{22}$"),
                "El CBU debe tener exactamente 22 dígitos");
    }

    // --- Apertura (POST /api/v1/cuentas) ---

    @Test
    void AC001_aperturaCajaAhorroSinMonedaResponde201ConDatosCompletos() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000001", "ac001-cuenta@example.com");

        // FR-001/FR-008/A-005: sin moneda → ARS por defecto.
        MvcResult result = mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(clienteId, "CAJA_AHORRO", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/cuentas/")))
                .andExpect(jsonPath("$.tipo").value("CAJA_AHORRO"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());

        assertTrue(json.get("id").asLong() > 0);
        assertEquals(clienteId, json.get("clienteId").asLong());
        assertSaldoCero(json);
        assertCbuDe22Digitos(json);
    }

    @Test
    void AC002_aperturaCuentaCorrienteConArsExplicitoResponde201() throws Exception {
        long clienteId = crearClienteAdmin("Bruno", "Diaz", "40000002", "ac002-cuenta@example.com");

        JsonNode json = crearCuentaAdmin(clienteId, "CUENTA_CORRIENTE", "ARS");

        assertEquals("CUENTA_CORRIENTE", json.get("tipo").asText());
        assertEquals("ARS", json.get("moneda").asText());
        assertSaldoCero(json);
    }

    @Test
    void AC003_monedaUsdResponde422MonedaNoSoportada() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000003", "ac003-cuenta@example.com");

        mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(clienteId, "CAJA_AHORRO", "USD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MONEDA_NO_SOPORTADA"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void AC004_tipoInvalidoResponde400ConCampoTipo() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000004", "ac004-cuenta@example.com");

        mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(clienteId, "ahorro", "ARS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("tipo"));
    }

    @Test
    void AC005_clienteInexistenteResponde404ClienteNoEncontrado() throws Exception {
        mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(999999L, "CAJA_AHORRO", "ARS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENTE_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.message").value("Cliente no encontrado"));
    }

    @Test
    void AC006_aperturaSinTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/v1/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(1L, "CAJA_AHORRO", "ARS")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
    }

    @Test
    void AC007_aperturaConTokenClienteResponde403() throws Exception {
        mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", 1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCuenta(1L, "CAJA_AHORRO", "ARS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void ERR001_aperturaSinClienteIdResponde400ConCampoClienteId() throws Exception {
        // ERR-001: clienteId es el campo obligatorio de FR-001; sin él la
        // apertura responde 400 DATOS_INVALIDOS (antes caía en el fallback 500).
        mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CAJA_AHORRO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("clienteId"));
    }

    // --- Consulta por id (GET /api/v1/cuentas/{id}) ---

    @Test
    void AC008_adminConsultaCualquierCuentaYRecibe200ConTodosLosDatos() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000008", "ac008-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");
        long id = creada.get("id").asLong();

        mockMvc.perform(get("/api/v1/cuentas/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.cbu").value(creada.get("cbu").asText()))
                .andExpect(jsonPath("$.tipo").value("CAJA_AHORRO"))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void AC009_clienteConsultaCuentaPropiaYRecibe200() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000009", "ac009-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");
        long id = creada.get("id").asLong();

        mockMvc.perform(get("/api/v1/cuentas/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void AC010_clienteConsultaCuentaAjenaYRecibe403() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000010", "ac010-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");

        // Claim con otro clienteId (ajeno) → 403 (AF-002, ERR-007).
        mockMvc.perform(get("/api/v1/cuentas/{id}", creada.get("id").asLong())
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", 999999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC011_idInexistenteResponde404CuentaNoEncontrada() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas/{id}", 999999)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUENTA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.message").value("Cuenta no encontrada"));
    }

    @Test
    void AC012_idNoNumericoResponde400() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas/abc")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
    }

    // --- Consulta por cbu (GET /api/v1/cuentas/cbu/{cbu}) ---

    @Test
    void AC013_adminConsultaPorCbuExistenteYRecibe200() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000013", "ac013-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");

        mockMvc.perform(get("/api/v1/cuentas/cbu/{cbu}", creada.get("cbu").asText())
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cbu").value(creada.get("cbu").asText()))
                .andExpect(jsonPath("$.id").value(creada.get("id").asLong()));
    }

    @Test
    void AC014_clienteConsultaCbuPropioYRecibe200() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000014", "ac014-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");

        mockMvc.perform(get("/api/v1/cuentas/cbu/{cbu}", creada.get("cbu").asText())
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cbu").value(creada.get("cbu").asText()));
    }

    @Test
    void AC015_clienteConsultaCbuAjenoYRecibe403() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000015", "ac015-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");

        mockMvc.perform(get("/api/v1/cuentas/cbu/{cbu}", creada.get("cbu").asText())
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", 999999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC016_cbuInexistenteResponde404CuentaNoEncontrada() throws Exception {
        // CBU con formato válido (22 dígitos) que no existe.
        mockMvc.perform(get("/api/v1/cuentas/cbu/{cbu}", "9999999999999999999999")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUENTA_NO_ENCONTRADA"));
    }

    @Test
    void AC017_cbuMalformadoResponde400CbuInvalido() throws Exception {
        // 21 dígitos: el VO CBU lo rechaza (BR-001, ERR-005).
        mockMvc.perform(get("/api/v1/cuentas/cbu/{cbu}", "123456789012345678901")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CBU_INVALIDO"))
                .andExpect(jsonPath("$.details[0].campo").value("cbu"));
    }

    // --- Listado (GET /api/v1/cuentas) ---

    @Test
    void AC018_clienteListaSoloSusCuentas() throws Exception {
        long propio = crearClienteAdmin("Ana", "Lopez", "40000018", "ac018-cuenta@example.com");
        JsonNode cuentaPropia = crearCuentaAdmin(propio, "CAJA_AHORRO", "ARS");
        // Otra cuenta de otro cliente: NO debe aparecer en el listado del CLIENTE.
        long otro = crearClienteAdmin("Bruno", "Diaz", "40000019", "ac018b-cuenta@example.com");
        crearCuentaAdmin(otro, "CUENTA_CORRIENTE", "ARS");

        MvcResult result = mockMvc.perform(get("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", propio)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());

        assertTrue(array.size() >= 1, "El CLIENTE debe ver al menos su cuenta");
        boolean encontrada = false;
        for (JsonNode item : array) {
            assertEquals(propio, item.get("clienteId").asLong(),
                    "El CLIENTE solo ve sus propias cuentas");
            if (item.get("id").asLong() == cuentaPropia.get("id").asLong()) {
                encontrada = true;
            }
        }
        assertTrue(encontrada, "El listado debe incluir la cuenta propia");
    }

    @Test
    void AC019_clienteConParametroClienteIdResponde403() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000020", "ac019-cuenta@example.com");

        // A-004/ERR-007: el CLIENTE no envía el parámetro clienteId.
        mockMvc.perform(get("/api/v1/cuentas?clienteId={id}", clienteId)
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC020_adminListaConFiltroValidoYRecibeSoloLasCuentasDeEseCliente() throws Exception {
        long filtro = crearClienteAdmin("Ana", "Lopez", "40000021", "ac020-cuenta@example.com");
        JsonNode cuentaFiltro = crearCuentaAdmin(filtro, "CAJA_AHORRO", "ARS");
        long otro = crearClienteAdmin("Bruno", "Diaz", "40000022", "ac020b-cuenta@example.com");
        crearCuentaAdmin(otro, "CUENTA_CORRIENTE", "ARS");

        MvcResult result = mockMvc.perform(get("/api/v1/cuentas?clienteId={id}", filtro)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());

        assertTrue(array.size() >= 1);
        boolean encontrada = false;
        for (JsonNode item : array) {
            assertEquals(filtro, item.get("clienteId").asLong());
            if (item.get("id").asLong() == cuentaFiltro.get("id").asLong()) {
                encontrada = true;
            }
        }
        assertTrue(encontrada, "El listado filtrado debe incluir la cuenta del cliente");
    }

    @Test
    void AC021_adminListaConClienteInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas?clienteId={id}", 999999)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void AC022_adminListaTodasLasCuentasOrdenadasPorIdAsc() throws Exception {
        long c1 = crearClienteAdmin("Ana", "Lopez", "40000023", "ac022-cuenta@example.com");
        crearCuentaAdmin(c1, "CAJA_AHORRO", "ARS");
        long c2 = crearClienteAdmin("Bruno", "Diaz", "40000024", "ac022b-cuenta@example.com");
        crearCuentaAdmin(c2, "CUENTA_CORRIENTE", "ARS");

        // El listado incluye cuentas de tests anteriores (BD por clase); todas
        // deben venir ordenadas por id ascendente.
        MvcResult result = mockMvc.perform(get("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", greaterThan(0)))
                .andReturn();
        JsonNode array = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());

        for (int i = 0; i < array.size() - 1; i++) {
            long actual = array.get(i).get("id").asLong();
            long siguiente = array.get(i + 1).get("id").asLong();
            assertTrue(actual < siguiente,
                    "Lista no ordenada por id ascendente en posición " + i);
        }
    }

    @Test
    void AC023_clienteSinClienteIdEnElClaimResponde403() throws Exception {
        // tokenCliente con clienteId null → el claim clienteId no se emite
        // (JwtTokenFactory) → AuthenticatedUser(null, "CLIENTE") → 403 (AF-001).
        mockMvc.perform(get("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    // --- Persistencia y constraints (AC-029) ---

    @Test
    void AC029_laCuentaCreadaPersisteEntreRequests() throws Exception {
        long clienteId = crearClienteAdmin("Persistente", "Cuenta", "40000029", "ac029-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");
        long id = creada.get("id").asLong();
        String cbu = creada.get("cbu").asText();

        // Consulta por id y por cbu en requests posteriores: persistencia real.
        mockMvc.perform(get("/api/v1/cuentas/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cbu").value(cbu));
        mockMvc.perform(get("/api/v1/cuentas/cbu/{cbu}", cbu)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void AC029_elConstraintUniqueDeCbuRechazaUnCbuDuplicado() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000030", "ac029b-cuenta@example.com");
        JsonNode creada = crearCuentaAdmin(clienteId, "CAJA_AHORRO", "ARS");
        String cbu = creada.get("cbu").asText();

        // Insert directo con el mismo CBU → viola uq_cuentas_cbu (BR-001).
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO cuentas (cliente_id, cbu, tipo, saldo, moneda, estado, version, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        clienteId, cbu, "CAJA_AHORRO", BigDecimal.ZERO, "ARS", "ACTIVA", 0L,
                        Timestamp.from(Instant.now())));
    }

    @Test
    void AC029_laFkAClientesRechazaUnClienteInexistente() throws Exception {
        // cliente_id inexistente → viola la FK a clientes(id) (ERR-002, garantía final).
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO cuentas (cliente_id, cbu, tipo, saldo, moneda, estado, version, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        999999L, "1111111111111111111111", "CAJA_AHORRO", BigDecimal.ZERO, "ARS",
                        "ACTIVA", 0L, Timestamp.from(Instant.now())));
    }

    @Test
    void AC029_laViolacionDeIntegridadSeMapeaA409() {
        // ERR-010: no existe camino HTTP que dispare la violación de UNIQUE en
        // este sprint (el CBU nunca llega por request); se verifica el mapeo
        // invocando directamente el bean del handler (AC-029).
        ErrorResponse response = globalExceptionHandler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("violación de constraint"));

        assertEquals("CONFLICTO_UNICIDAD", response.code());
        assertEquals("Conflicto de unicidad de datos", response.message());
        assertNull(response.details());
    }
}
