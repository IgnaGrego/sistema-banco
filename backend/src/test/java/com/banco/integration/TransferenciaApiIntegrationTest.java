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

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint de transferencias e historial con Testcontainers + MockMvc
 * (AC-001..AC-015, AC-016..AC-020, AC-022): un método por criterio. Patrón de
 * {@code CuentaApiIntegrationTest}: {@code @TestConfiguration TokenConfig} anidada
 * declara el bean {@link JwtTokenFactory}; clientes y cuentas se crean vía la API
 * (SPEC-002 ya implementada — ADR-007) y el fondeo de saldo con {@code JdbcTemplate}.
 *
 * <p>Usernames/DNIs de token ÚNICOS por método (el contenedor Postgres es por
 * clase — riesgo documentado en SPEC-004 §12).
 */
@Testcontainers(disabledWithoutDocker = true)
class TransferenciaApiIntegrationTest extends BaseIntegrationTest {

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

    private String bodyTransferencia(long cuentaOrigenId, String cbuDestino, String monto) {
        return """
                {"cuentaOrigenId":%s,"cbuDestino":"%s","monto":%s}
                """.formatted(cuentaOrigenId, cbuDestino, monto);
    }

    private MvcResult transferir(String token, long cuentaOrigenId, String cbuDestino,
                                 String monto) throws Exception {
        return mockMvc.perform(post("/api/v1/transferencias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTransferencia(cuentaOrigenId, cbuDestino, monto)))
                .andReturn();
    }

    private void fondear(long cuentaId, String monto) {
        // A-003/A-010: la apertura crea la cuenta con saldo 0 (SPEC-002); el
        // fondeo directo en BD no toca la versión (el @Version sigue en 0).
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

    // --- Transferencia (AC-001..AC-015) ---

    @Test
    void AC001_transferenciaValidaResponde201ConConfirmacionYSaldosActualizados() throws Exception {
        long clienteOrigen = crearClienteAdmin("Ana", "Lopez", "40000001", "ac001-tx@example.com");
        long clienteDestino = crearClienteAdmin("Bruno", "Diaz", "40000002", "ac001b-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        JsonNode destino = abrirCuentaAdmin(clienteDestino, "CAJA_AHORRO");
        long origenId = origen.get("id").asLong();
        long destinoId = destino.get("id").asLong();
        fondear(origenId, "100000.00");

        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteOrigen),
                origenId, destino.get("cbu").asText(), "15000.00");

        // FR-001: 201 con idTransferencia/monto/cbuDestino/fechaHora (A-007).
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals(201, result.getResponse().getStatus());
        assertTrue(json.get("idTransferencia").asLong() > 0);
        assertEquals(0, json.get("monto").decimalValue().compareTo(new BigDecimal("15000.00")));
        assertEquals(destino.get("cbu").asText(), json.get("cbuDestino").asText());
        assertTrue(json.has("fechaHora"));

        // FR-002/BR-001: el saldo de origen se debita y el de destino se
        // acredita exactamente por el monto (AC-001).
        assertEquals(0, saldoDe(origenId).compareTo(new BigDecimal("85000.00")));
        assertEquals(0, saldoDe(destinoId).compareTo(new BigDecimal("15000.00")));

        // FR-003/AC-002: exactamente un movimiento por cuenta (2 en total),
        // SALIENTE en origen y ENTRANTE en destino.
        assertEquals(1, cantidadMovimientosDe(origenId));
        assertEquals(1, cantidadMovimientosDe(destinoId));
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", origenId)
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteOrigen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("TRANSFERENCIA_SALIENTE"))
                .andExpect(jsonPath("$[0].monto").value(15000.00))
                .andExpect(jsonPath("$[0].cuentaContraparteId").value(destinoId))
                .andExpect(jsonPath("$[0].moneda").value("ARS"));
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", destinoId)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("TRANSFERENCIA_ENTRANTE"))
                .andExpect(jsonPath("$[0].cuentaContraparteId").value(origenId));
    }

    @Test
    void AC004_saldoInsuficienteResponde422SinModificarSaldosNiMovimientos() throws Exception {
        long clienteOrigen = crearClienteAdmin("Ana", "Lopez", "40000003", "ac004-tx@example.com");
        long clienteDestino = crearClienteAdmin("Bruno", "Diaz", "40000004", "ac004b-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        JsonNode destino = abrirCuentaAdmin(clienteDestino, "CAJA_AHORRO");
        long origenId = origen.get("id").asLong();
        long destinoId = destino.get("id").asLong();
        fondear(origenId, "100.00");

        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteOrigen),
                origenId, destino.get("cbu").asText(), "150.00");

        // ERR-001: 422 SALDO_INSUFICIENTE sin débito ni movimientos (AC-004).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("SALDO_INSUFICIENTE", json.get("code").asText());
        assertEquals(0, saldoDe(origenId).compareTo(new BigDecimal("100.00")));
        assertEquals(0, saldoDe(destinoId).compareTo(BigDecimal.ZERO));
        assertEquals(0, cantidadMovimientosDe(origenId));
        assertEquals(0, cantidadMovimientosDe(destinoId));
    }

    @Test
    void AC009_transferirASiMismaResponde422AutoTransferencia() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000005", "ac009-tx@example.com");
        JsonNode cuenta = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        fondear(cuenta.get("id").asLong(), "1000.00");

        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteId),
                cuenta.get("id").asLong(), cuenta.get("cbu").asText(), "100.00");

        // ERR-008/BR-005: 422 AUTO_TRANSFERENCIA (AC-009).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("AUTO_TRANSFERENCIA", json.get("code").asText());
        assertEquals(0, cantidadMovimientosDe(cuenta.get("id").asLong()));
    }

    @Test
    void AC010_limiteDiarioAlcanzadoResponde422LimiteDiarioExcedidoSinDebito() throws Exception {
        long clienteOrigen = crearClienteAdmin("Ana", "Lopez", "40000006", "ac010-tx@example.com");
        long clienteDestino = crearClienteAdmin("Bruno", "Diaz", "40000007", "ac010b-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        JsonNode destino = abrirCuentaAdmin(clienteDestino, "CAJA_AHORRO");
        long origenId = origen.get("id").asLong();
        long destinoId = destino.get("id").asLong();
        // Default 200000 (A-002): total 0 + monto 200000 >= 200000 → rechazado
        // (AF-002/AC-010). El saldo de 250000 alcanza pero el límite no.
        fondear(origenId, "250000.00");

        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteOrigen),
                origenId, destino.get("cbu").asText(), "200000.00");

        // ERR-007: 422 LIMITE_DIARIO_EXCEDIDO, sin débito (AC-010).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("LIMITE_DIARIO_EXCEDIDO", json.get("code").asText());
        assertEquals(0, saldoDe(origenId).compareTo(new BigDecimal("250000.00")));
        assertEquals(0, cantidadMovimientosDe(origenId));
    }

    @Test
    void AC011_elLimiteDiarioSeComputaSobreTodasLasCuentasDelCliente() throws Exception {
        long clienteOrigen = crearClienteAdmin("Ana", "Lopez", "40000008", "ac011-tx@example.com");
        long clienteDestino = crearClienteAdmin("Bruno", "Diaz", "40000009", "ac011b-tx@example.com");
        JsonNode origen1 = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        JsonNode origen2 = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        JsonNode destino = abrirCuentaAdmin(clienteDestino, "CAJA_AHORRO");
        fondear(origen1.get("id").asLong(), "120000.00");
        fondear(origen2.get("id").asLong(), "90000.00");

        String token = tokens.tokenCliente("cliente-test", clienteOrigen);

        // 1ª transferencia: 120000 (por debajo del límite) → 201.
        MvcResult primera = transferir(token, origen1.get("id").asLong(),
                destino.get("cbu").asText(), "120000.00");
        assertEquals(201, primera.getResponse().getStatus());

        // 2ª desde OTRA cuenta del mismo cliente: 120000 + 90000 >= 200000 → 422
        // (BR-004: el límite se computa por cliente, todas sus cuentas — AC-011).
        MvcResult segunda = transferir(token, origen2.get("id").asLong(),
                destino.get("cbu").asText(), "90000.00");
        assertEquals(422, segunda.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(segunda.getResponse().getContentAsString());
        assertEquals("LIMITE_DIARIO_EXCEDIDO", json.get("code").asText());
        assertEquals(0, saldoDe(origen2.get("id").asLong()).compareTo(new BigDecimal("90000.00")));
    }

    @Test
    void AC013_transferirDesdeCuentaAjenaResponde403AccesoDenegado() throws Exception {
        long dueno = crearClienteAdmin("Ana", "Lopez", "40000010", "ac013-tx@example.com");
        JsonNode cuenta = abrirCuentaAdmin(dueno, "CAJA_AHORRO");
        fondear(cuenta.get("id").asLong(), "1000.00");
        // Cuenta destino de otro cliente (el origen es ajeno al claim).
        long otro = crearClienteAdmin("Bruno", "Diaz", "40000011", "ac013b-tx@example.com");
        JsonNode destino = abrirCuentaAdmin(otro, "CAJA_AHORRO");

        MvcResult result = transferir(tokens.tokenCliente("cliente-test", 999999L),
                cuenta.get("id").asLong(), destino.get("cbu").asText(), "100.00");

        // ERR-006/AC-013: 403 ACCESO_DENEGADO (el claim no es el titular del
        // origen); sin débito.
        assertEquals(403, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("ACCESO_DENEGADO", json.get("code").asText());
        assertEquals(0, saldoDe(cuenta.get("id").asLong()).compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void AC005_cbuDestinoInexistenteResponde404SinDebito() throws Exception {
        long clienteOrigen = crearClienteAdmin("Ana", "Lopez", "40000012", "ac005-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        fondear(origen.get("id").asLong(), "1000.00");

        // CBU con formato válido (22 dígitos) sin cuenta asociada (AF-001/AC-005).
        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteOrigen),
                origen.get("id").asLong(), "9999999999999999999999", "100.00");

        assertEquals(404, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CUENTA_NO_ENCONTRADA", json.get("code").asText());
        assertEquals(0, saldoDe(origen.get("id").asLong()).compareTo(new BigDecimal("1000.00")));
        assertEquals(0, cantidadMovimientosDe(origen.get("id").asLong()));
    }

    @Test
    void AF003_transferenciaEntreDosCuentasPropiasResponde201() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000013", "af003-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        JsonNode destino = abrirCuentaAdmin(clienteId, "CUENTA_CORRIENTE");
        fondear(origen.get("id").asLong(), "50000.00");

        // BR-005: dos cuentas DISTINTAS del mismo cliente → permitido (AF-003).
        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteId),
                origen.get("id").asLong(), destino.get("cbu").asText(), "30000.00");

        assertEquals(201, result.getResponse().getStatus());
        assertEquals(0, saldoDe(origen.get("id").asLong()).compareTo(new BigDecimal("20000.00")));
        assertEquals(0, saldoDe(destino.get("id").asLong()).compareTo(new BigDecimal("30000.00")));
        assertEquals(1, cantidadMovimientosDe(origen.get("id").asLong()));
        assertEquals(1, cantidadMovimientosDe(destino.get("id").asLong()));
    }

    @Test
    void AC014_sinTokenResponde401YTokenAdminResponde403() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000014", "ac014-tx@example.com");
        JsonNode cuenta = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        fondear(cuenta.get("id").asLong(), "1000.00");

        // AC-020 (FR-003): sin token → 401.
        mockMvc.perform(post("/api/v1/transferencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTransferencia(cuenta.get("id").asLong(),
                                cuenta.get("cbu").asText(), "100.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));

        // AC-014 (A-005): el ADMIN no inicia transferencias → 403.
        mockMvc.perform(post("/api/v1/transferencias")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTransferencia(cuenta.get("id").asLong(),
                                cuenta.get("cbu").asText(), "100.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC008_montoInvalidoResponde400ConCampoMonto() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000015", "ac008-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        long clienteDestino = crearClienteAdmin("Bruno", "Diaz", "40000018", "ac008b-tx@example.com");
        JsonNode destino = abrirCuentaAdmin(clienteDestino, "CAJA_AHORRO");
        fondear(origen.get("id").asLong(), "1000.00");

        // ERR-004/BR-003: monto <= 0 o con más de 2 decimales → 400 DATOS_INVALIDOS
        // con details[0].campo == "monto" (AC-008). El CBU destino es una cuenta
        // válida ajena: el chequeo del monto (paso 7 de la cadena) ocurre antes
        // de llegar al saldo/límite, así el 400 es por el monto.
        mockMvc.perform(post("/api/v1/transferencias")
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTransferencia(origen.get("id").asLong(),
                                destino.get("cbu").asText(), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("monto"));
        mockMvc.perform(post("/api/v1/transferencias")
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyTransferencia(origen.get("id").asLong(),
                                destino.get("cbu").asText(), "100.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("monto"));
    }

    @Test
    void AC006_cuentaOrigenBloqueadaResponde422CuentaBloqueada() throws Exception {
        long clienteOrigen = crearClienteAdmin("Ana", "Lopez", "40000016", "ac006-tx@example.com");
        long clienteDestino = crearClienteAdmin("Bruno", "Diaz", "40000017", "ac006b-tx@example.com");
        JsonNode origen = abrirCuentaAdmin(clienteOrigen, "CAJA_AHORRO");
        JsonNode destino = abrirCuentaAdmin(clienteDestino, "CAJA_AHORRO");
        fondear(origen.get("id").asLong(), "1000.00");
        // Se bloquea la cuenta vía el agregado + persistencia (AC-006).
        Cuenta cuenta = cuentaRepository.findById(origen.get("id").asLong()).orElseThrow();
        cuenta.bloquear();
        cuentaRepository.save(cuenta);

        MvcResult result = transferir(tokens.tokenCliente("cliente-test", clienteOrigen),
                origen.get("id").asLong(), destino.get("cbu").asText(), "100.00");

        // ERR-003/BR-002: 422 CUENTA_BLOQUEADA (AC-006).
        assertEquals(422, result.getResponse().getStatus());
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CUENTA_BLOQUEADA", json.get("code").asText());
        assertEquals(0, saldoDe(origen.get("id").asLong()).compareTo(new BigDecimal("1000.00")));
    }

    // --- Concurrencia (AC-012, docs/architecture/SPEC-004.md §8.9) ---

    @Test
    void AC012_dosTransferenciasConcurrentesSobreElMismoOrigenUna201YLaOtra409() throws Exception {
        // Parte 1: dos requests SIMULTÁNEAS de 60000 sobre un origen con 100000
        // (A <= S, B <= S, A + B > S). El resultado esperado es (201, 409); si
        // el scheduler se serializa el resultado es (201, 422) y se reintenta
        // con datos frescos (mitigación de flake — SPEC-004 §12).
        boolean exito = false;
        for (int intento = 0; intento < 3 && !exito; intento++) {
            String sufijo = "ac012-" + intento;
            long clienteId = crearClienteAdmin("Ana", "Lopez", "4000003" + intento, sufijo + "-tx@example.com");
            JsonNode origen = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
            JsonNode destino = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
            long origenId = origen.get("id").asLong();
            fondear(origenId, "100000.00");
            String cbuDestino = destino.get("cbu").asText();
            String token = tokens.tokenCliente("cliente-test", clienteId);

            int n = 2;
            CyclicBarrier barrera = new CyclicBarrier(n);
            ExecutorService executor = Executors.newFixedThreadPool(n);
            try {
                List<Future<Integer>> resultados = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    resultados.add(executor.submit(() -> {
                        barrera.await();
                        return mockMvc.perform(post("/api/v1/transferencias")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bodyTransferencia(origenId, cbuDestino, "60000.00")))
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
                    // Saldo final consistente: 100000 - 60000 (el ganador), nunca
                    // negativo (BR-006, AC-012); exactamente 1 movimiento saliente.
                    assertEquals(0, saldoDe(origenId).compareTo(new BigDecimal("40000.00")));
                    assertEquals(1, cantidadMovimientosDe(origenId));
                    exito = true;
                }
                // Si no, reintento con datos frescos (schedule serializado).
            } finally {
                executor.shutdownNow();
            }
        }
        assertTrue(exito, "Se esperaba exactamente una 201 y una 409 en la carrera (AC-012)");
    }

    @Test
    void AC012_conflictoDeterministaDeVersionAlPersistir() throws Exception {
        // Parte 2: sin HTTP — verifica que el adapter mapea @Version en ambos
        // sentidos (docs/architecture/SPEC-004.md §8.8): si el mapeo faltara,
        // el merge usaría version 0 y el lock nunca dispararía.
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000019", "ac012b-tx@example.com");
        JsonNode cuentaJson = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");
        long cuentaId = cuentaJson.get("id").asLong();
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

    // --- Historial (AC-016..AC-020) ---

    @Test
    void AC017_clienteConsultaHistorialDeCuentaAjenaResponde403() throws Exception {
        long dueno = crearClienteAdmin("Ana", "Lopez", "40000020", "ac017-tx@example.com");
        JsonNode cuenta = abrirCuentaAdmin(dueno, "CAJA_AHORRO");

        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", cuenta.get("id").asLong())
                        .header("Authorization", "Bearer " + tokens.tokenCliente("cliente-test", 999999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC018_adminConsultaHistorialDeCualquierCuentaResponde200() throws Exception {
        long clienteId = crearClienteAdmin("Ana", "Lopez", "40000021", "ac018-tx@example.com");
        JsonNode cuenta = abrirCuentaAdmin(clienteId, "CAJA_AHORRO");

        // A-005: el ADMIN consulta cualquier cuenta; lista vacía si no hay
        // movimientos.
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", cuenta.get("id").asLong())
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void AC019_historialDeCuentaInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", 999999)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin("admin-test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUENTA_NO_ENCONTRADA"));
    }

    @Test
    void AC020_historialSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/cuentas/{id}/movimientos", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
    }
}
