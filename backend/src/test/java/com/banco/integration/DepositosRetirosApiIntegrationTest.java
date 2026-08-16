package com.banco.integration;

import com.banco.domain.model.Cuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.Money;
import com.banco.support.JwtTokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de depósitos y retiros con Testcontainers + MockMvc
 * (AC-001..AC-017, AC-019): un método por criterio, incluida la concurrencia
 * (AC-015) en dos partes (docs/architecture/SPEC-005.md §8.9). Patrón de
 * {@code TransferenciaApiIntegrationTest}: {@code @TestConfiguration TokenConfig}
 * anidada declara el bean {@link JwtTokenFactory}; clientes y cuentas se crean
 * vía la API (SPEC-002 ya implementada) y el fondeo de saldo con
 * {@code JdbcTemplate} (no toca la versión — el {@code @Version} sigue en 0).
 *
 * <p>Usernames/DNIs de token ÚNICOS por método (el contenedor Postgres es por
 * clase — riesgo documentado en SPEC-005 §12).
 */
@Testcontainers(disabledWithoutDocker = true)
class DepositosRetirosApiIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CuentaRepository cuentaRepository;

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

    private JsonNode abrirCuentaAdmin(long clienteId, String tipo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cuentas")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":%s,"tipo":"%s","moneda":"ARS"}
                                """.formatted(clienteId, tipo)))
                .andExpect(status().isCreated())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private String bodyDeposito(long cuentaId, String monto) {
        return """
                {"cuentaId":%s,"monto":%s}
                """.formatted(cuentaId, monto);
    }

    private String bodyRetiro(long cuentaId, String monto) {
        return """
                {"cuentaId":%s,"monto":%s}
                """.formatted(cuentaId, monto);
    }

    private MvcResult depositar(String token, long cuentaId, String monto) throws Exception {
        return mockMvc.perform(post("/api/v1/depositos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDeposito(cuentaId, monto)))
                .andReturn();
    }

    private MvcResult retirar(String token, long cuentaId, String monto) throws Exception {
        return mockMvc.perform(post("/api/v1/retiros")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRetiro(cuentaId, monto)))
                .andReturn();
    }

    private void fondear(long cuentaId, String monto) {
        // El fondeo directo en BD no toca la versión (el @Version sigue en 0).
        jdbcTemplate.update("UPDATE cuentas SET saldo = ? WHERE id = ?",
                new BigDecimal(monto), cuentaId);
    }

    private BigDecimal saldoDe(long cuentaId) {
        return jdbcTemplate.queryForObject("SELECT saldo FROM cuentas WHERE id = ?",
                BigDecimal.class, cuentaId);
    }

    private Integer cantidadMovimientosDe(long cuentaId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movimientos WHERE cuenta_id = ?",
                Integer.class, cuentaId);
    }

    // --- Depósito (AC-001..AC-006) ---

    @Test
    void AC001_adminDepositaMontoValidoResponde201ConConfirmacionYSaldoIncrementado() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000001", "ac001-dep@example.com");
        JsonNode cuenta = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        long cuentaId = cuenta.get("id").asLong();

        MvcResult result = depositar(tokens.tokenAdmin("admin-test"), cuentaId, "15000.00");

        // FR-001/A-002: 201 con idMovimiento/cuentaId/monto/fechaHora (AC-001).
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals(201, result.getResponse().getStatus());
        assertTrue(json.get("idMovimiento").asLong() > 0);
        assertEquals(cuentaId, json.get("cuentaId").asLong());
        assertEquals(0, json.get("monto").decimalValue().compareTo(new BigDecimal("15000.00")));
        assertTrue(json.has("fechaHora"));

        // BR-001/BR-002: el saldo se incrementa exactamente por el monto.
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("15000.00")));

        // FR-003/AC-001: exactamente 1 movimiento DEPOSITO.
        assertEquals(1, cantidadMovimientosDe(cuentaId));
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", cuentaId)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$[0].monto").value(15000.00))
                .andExpect(jsonPath("$[0].moneda").value("ARS"));
    }

    @Test
    void AC003_depositoConMontoInvalidoResponde400ConCampoMonto() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000002", "ac003-dep@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        String token = tokens.tokenAdmin("admin-test");

        // ERR-001/BR-001: monto 0, negativo o con más de 2 decimales → 400
        // DATOS_INVALIDOS con details[0].campo == "monto" (AC-003).
        for (String monto : List.of("0", "-5", "100.123")) {
            MvcResult result = depositar(token, cuentaId, monto);
            assertEquals(400, result.getResponse().getStatus());
            JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            assertEquals("DATOS_INVALIDOS", json.get("code").asText());
            assertEquals("monto", json.get("details").get(0).get("campo").asText());
        }

        // Monto no numérico ("abc") → HttpMessageNotReadableException → 400
        // DATOS_INVALIDOS sin details (ERR-001, §8.4).
        mockMvc.perform(post("/api/v1/depositos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaId":%s,"monto":"abc"}
                                """.formatted(cuentaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));

        // Sin cambios de saldo ni movimientos.
        assertEquals(0, saldoDe(cuentaId).compareTo(BigDecimal.ZERO));
        assertEquals(0, cantidadMovimientosDe(cuentaId));
    }

    @Test
    void AC004_depositoEnCuentaInexistenteResponde404() throws Exception {
        MvcResult result = depositar(tokens.tokenAdmin("admin-test"), 999999L, "100.00");

        // ERR-003: 404 CUENTA_NO_ENCONTRADA (AC-004).
        assertEquals(404, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CUENTA_NO_ENCONTRADA", json.get("code").asText());
    }

    @Test
    void AC005_depositoEnCuentaBloqueadaResponde422() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000003", "ac005-dep@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        // Se bloquea la cuenta vía el agregado + persistencia (AC-005).
        Cuenta cuenta = cuentaRepository.findById(cuentaId).orElseThrow();
        cuenta.bloquear();
        cuentaRepository.save(cuenta);

        MvcResult result = depositar(tokens.tokenAdmin("admin-test"), cuentaId, "100.00");

        // ERR-004/BR-003: 422 CUENTA_BLOQUEADA (AC-005).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CUENTA_BLOQUEADA", json.get("code").asText());
        assertEquals(0, cantidadMovimientosDe(cuentaId));
    }

    @Test
    void AC006_clienteDepositaInclusoEnSuPropiaCuentaResponde403() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000004", "ac006-dep@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();

        // A-001/ERR-005: un CLIENTE nunca deposita, ni siquiera en su propia
        // cuenta → 403 ACCESO_DENEGADO del matcher hasRole("ADMIN") (AC-006).
        MvcResult result = depositar(tokens.tokenCliente("cliente-test", clienteId),
                cuentaId, "100.00");

        assertEquals(403, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("ACCESO_DENEGADO", json.get("code").asText());
        assertEquals(0, saldoDe(cuentaId).compareTo(BigDecimal.ZERO));
        assertEquals(0, cantidadMovimientosDe(cuentaId));
    }

    // --- Retiro (AC-007..AC-014, AC-017) ---

    @Test
    void AC007_adminRetiraMontoValidoResponde201ConConfirmacionYSaldoDecrementado() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000005", "ac007-ret@example.com");
        JsonNode cuenta = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        long cuentaId = cuenta.get("id").asLong();
        fondear(cuentaId, "100000.00");

        MvcResult result = retirar(tokens.tokenAdmin("admin-test"), cuentaId, "40000.00");

        // FR-002/A-002: 201 con idMovimiento/cuentaId/monto/fechaHora (AC-007).
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals(201, result.getResponse().getStatus());
        assertTrue(json.get("idMovimiento").asLong() > 0);
        assertEquals(cuentaId, json.get("cuentaId").asLong());
        assertEquals(0, json.get("monto").decimalValue().compareTo(new BigDecimal("40000.00")));
        assertTrue(json.has("fechaHora"));

        // BR-002: el saldo se decrementa exactamente por el monto (AC-007).
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("60000.00")));

        // FR-003: exactamente 1 movimiento RETIRO.
        assertEquals(1, cantidadMovimientosDe(cuentaId));
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", cuentaId)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RETIRO"))
                .andExpect(jsonPath("$[0].monto").value(40000.00))
                .andExpect(jsonPath("$[0].moneda").value("ARS"));
    }

    @Test
    void AC008_clienteRetiraDeSuPropiaCuentaResponde201() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000006", "ac008-ret@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "50000.00");

        MvcResult result = retirar(tokens.tokenCliente("cliente-test", clienteId),
                cuentaId, "20000.00");

        // FR-002/§9: 201; saldo decrementado y movimiento RETIRO (AC-008).
        assertEquals(201, result.getResponse().getStatus());
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("30000.00")));
        assertEquals(1, cantidadMovimientosDe(cuentaId));
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", cuentaId)
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RETIRO"));
    }

    @Test
    void AC010_retiroConSaldoInsuficienteResponde422SinCambios() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000007", "ac010-ret@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "100.00");

        MvcResult result = retirar(tokens.tokenCliente("cliente-test", clienteId),
                cuentaId, "150.00");

        // ERR-002/BR-002: 422 SALDO_INSUFICIENTE sin cambios de saldo ni
        // movimientos (AC-010).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("SALDO_INSUFICIENTE", json.get("code").asText());
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("100.00")));
        assertEquals(0, cantidadMovimientosDe(cuentaId));
    }

    @Test
    void AC011_retiroConMontoInvalidoResponde400ConCampoMonto() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000008", "ac011-ret@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "1000.00");

        // ERR-001/BR-001: monto <= 0 → 400 DATOS_INVALIDOS con
        // details[0].campo == "monto" (AC-011).
        for (String monto : List.of("0", "-5")) {
            MvcResult result = retirar(tokens.tokenCliente("cliente-test", clienteId),
                    cuentaId, monto);
            assertEquals(400, result.getResponse().getStatus());
            JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            assertEquals("DATOS_INVALIDOS", json.get("code").asText());
            assertEquals("monto", json.get("details").get(0).get("campo").asText());
        }
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("1000.00")));
        assertEquals(0, cantidadMovimientosDe(cuentaId));
    }

    @Test
    void AC012_retiroEnCuentaInexistenteResponde404() throws Exception {
        MvcResult result = retirar(tokens.tokenCliente("cliente-test", 999999L), 999999L, "100.00");

        // ERR-003: 404 CUENTA_NO_ENCONTRADA (AC-012).
        assertEquals(404, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CUENTA_NO_ENCONTRADA", json.get("code").asText());
    }

    @Test
    void AC013_retiroEnCuentaBloqueadaResponde422() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000009", "ac013-ret@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "1000.00");
        // Se bloquea la cuenta vía el agregado + persistencia (AC-013).
        Cuenta cuenta = cuentaRepository.findById(cuentaId).orElseThrow();
        cuenta.bloquear();
        cuentaRepository.save(cuenta);

        MvcResult result = retirar(tokens.tokenCliente("cliente-test", clienteId),
                cuentaId, "100.00");

        // ERR-004/BR-003: 422 CUENTA_BLOQUEADA (AC-013).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CUENTA_BLOQUEADA", json.get("code").asText());
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void AC014_clienteRetiraDeCuentaAjenaResponde403() throws Exception {
        long dueno = crearClienteAdmin("Ana", "Lopez", "41000010", "ac014-ret@example.com");
        long cuentaId = abrirCuentaAdmin(dueno, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "1000.00");

        // ERR-005/§9: un CLIENTE solo retira de cuentas propias; una cuenta
        // ajena → 403 ACCESO_DENEGADO (AC-014, chequeo del validador).
        MvcResult result = retirar(tokens.tokenCliente("cliente-test", 999999L),
                cuentaId, "100.00");

        assertEquals(403, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("ACCESO_DENEGADO", json.get("code").asText());
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("1000.00")));
        assertEquals(0, cantidadMovimientosDe(cuentaId));
    }

    @Test
    void AC017_adminRetiraDeUnaCuentaDeOtroClienteResponde201() throws Exception {
        long dueno = crearClienteAdmin("Ana", "Lopez", "41000011", "ac017-ret@example.com");
        long cuentaId = abrirCuentaAdmin(dueno, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "50000.00");

        // FR-002/§9: el ADMIN no tiene chequeo de propiedad — retira de la
        // cuenta de OTRO cliente → 201 (AC-017).
        MvcResult result = retirar(tokens.tokenAdmin("admin-test"), cuentaId, "10000.00");

        assertEquals(201, result.getResponse().getStatus());
        assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("40000.00")));
        assertEquals(1, cantidadMovimientosDe(cuentaId));
    }

    // --- Autorización general (AC-016) ---

    @Test
    void AC016_sinTokenResponde401EnAmbosEndpoints() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000012", "ac016-ret@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();

        // FR-003 de SPEC-003: sin token → 401 NO_AUTENTICADO (AC-016).
        mockMvc.perform(post("/api/v1/depositos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDeposito(cuentaId, "100.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
        mockMvc.perform(post("/api/v1/retiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyRetiro(cuentaId, "100.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
    }

    // --- Concurrencia (AC-015, docs/architecture/SPEC-005.md §8.9) ---

    @Test
    void AC015_dosRetirosConcurrentesSobreLaMismaCuentaUna201YLaOtra409() throws Exception {
        // Parte 1: dos requests SIMULTÁNEAS de 60000 sobre una cuenta con
        // 100000 (A <= S, B <= S, A + B > S). El resultado esperado es
        // (201, 409); si el scheduler se serializa el resultado es (201, 422)
        // y se reintenta con datos frescos (mitigación de flake — SPEC-005 §12).
        boolean exito = false;
        for (int intento = 0; intento < 3 && !exito; intento++) {
            String sufijo = "ac015-" + intento;
            long clienteId = crearClienteAdmin("Ana", "Lopez", "4100012" + intento, sufijo + "-tx@example.com");
            long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
            fondear(cuentaId, "100000.00");
            String token = tokens.tokenCliente("cliente-test", clienteId);

            int n = 2;
            CyclicBarrier barrera = new CyclicBarrier(n);
            ExecutorService executor = Executors.newFixedThreadPool(n);
            try {
                List<Future<Integer>> resultados = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    resultados.add(executor.submit(() -> {
                        barrera.await();
                        return mockMvc.perform(post("/api/v1/retiros")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bodyRetiro(cuentaId, "60000.00")))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                List<Integer> estados = new ArrayList<>();
                for (Future<Integer> futuro : resultados) {
                    estados.add(futuro.get());
                }
                long count201 = estados.stream().filter(s -> s == 201).count();
                long count409 = estados.stream().filter(s -> s == 409).count();
                if (count201 == 1 && count409 == 1) {
                    // Saldo final consistente: 100000 - 60000 (el ganador),
                    // nunca negativo (BR-004, AC-015); exactamente 1 RETIRO.
                    assertEquals(0, saldoDe(cuentaId).compareTo(new BigDecimal("40000.00")));
                    assertEquals(1, cantidadMovimientosDe(cuentaId));
                    exito = true;
                }
                // Si no, reintento con datos frescos (schedule serializado).
            } finally {
                executor.shutdownNow();
            }
        }
        assertTrue(exito, "Se esperaba exactamente una 201 y una 409 en la carrera (AC-015)");
    }

    @Test
    void AC015_conflictoDeterministaDeVersionAlPersistir() throws Exception {
        // Parte 2: sin HTTP — verifica que el adapter mapea @Version en ambos
        // sentidos (docs/architecture/SPEC-005.md §8.8): si el mapeo faltara,
        // el merge usaría version 0 y el lock nunca dispararía.
        long clienteId = crearClienteAdmin("Ana", "Lopez", "41000123", "ac015b-ret@example.com");
        long cuentaId = abrirCuentaAdmin(clienteId, "CAJA_AHORRO").get("id").asLong();
        fondear(cuentaId, "1000.00");

        Cuenta cuenta = cuentaRepository.findById(cuentaId).orElseThrow();
        assertEquals(0L, cuenta.getVersion());

        // Operación concurrente: otro tx ya commiteó version 1.
        jdbcTemplate.update("UPDATE cuentas SET version = version + 1 WHERE id = ?", cuentaId);

        // La copia vieja (version 0) intenta persistir tras mutar el saldo:
        // UPDATE ... WHERE id = ? AND version = 0 → 0 filas →
        // ObjectOptimisticLockingFailureException (→ 409 por el handler).
        cuenta.debitar(Money.ars(new BigDecimal("100.00")));
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> cuentaRepository.save(cuenta));
    }
}
