package com.banco.integration;

import com.banco.support.JwtTokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints REST de clientes con Testcontainers + MockMvc: un método por
 * criterio de aceptación (AC-001..AC-022) + verificación de persistencia real
 * y del envelope JSON en 400/401/403/404/409 (AC-024).
 *
 * {@code @Testcontainers(disabledWithoutDocker = true)} también aquí, de forma
 * defensiva: la herencia no debe causar fallo duro si no hay Docker.
 *
 * {@link TokenConfig} debe estar anidada en la clase de test EJECUTADA (no en
 * la superclase): Spring Boot solo auto-registra {@code @TestConfiguration}
 * anidadas en la clase bajo ejecución. Declara el bean {@link JwtTokenFactory}
 * con el secret de application-test.yml (mismo contrato de claims que JwtService).
 */
@Testcontainers(disabledWithoutDocker = true)
class ClienteApiIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    private String bodyCliente(String nombre, String apellido, String dni,
                               String email, String telefono) {
        return """
                {"nombre":"%s","apellido":"%s","dni":"%s","email":"%s","telefono":%s}
                """.formatted(nombre, apellido, dni, email,
                telefono == null ? "null" : "\"" + telefono + "\"");
    }

    private long crearClienteAdmin(String nombre, String apellido, String dni,
                                   String email, String telefono) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente(nombre, apellido, dni, email, telefono)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    // --- Alta (POST) ---

    @Test
    void AC001_crearConTokenAdminYDatossValidosResponde201ConIdYFechaAlta() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "10000001", "ac001@example.com", "+549112345678")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/clientes/")))
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.apellido").value("Perez"))
                .andExpect(jsonPath("$.dni").value("10000001"))
                .andExpect(jsonPath("$.email").value("ac001@example.com"))
                .andExpect(jsonPath("$.telefono").value("+549112345678"))
                .andExpect(jsonPath("$.fechaAlta").isNotEmpty());
    }

    @Test
    void AC002_dniDuplicadoResponde409IndicandoElCampo() throws Exception {
        crearClienteAdmin("Ana", "Lopez", "10000002", "ac002a@example.com", null);
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Otro", "Cliente", "10000002", "ac002b@example.com", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICTO_UNICIDAD"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details[0].campo").value("dni"));
    }

    @Test
    void AC003_emailDuplicadoResponde409IndicandoElCampo() throws Exception {
        crearClienteAdmin("Ana", "Lopez", "10000003", "ac003@example.com", null);
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Otro", "Cliente", "20000003", "ac003@example.com", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICTO_UNICIDAD"))
                .andExpect(jsonPath("$.details[0].campo").value("email"));
    }

    @Test
    void AC004_dniNoNumericoOLongitudIncorrectaResponde400() throws Exception {
        // no numérico
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "abcdefg", "ac004a@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("dni"));
        // 6 dígitos
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "123456", "ac004b@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].campo").value("dni"));
        // 9 dígitos
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "123456789", "ac004c@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].campo").value("dni"));
    }

    @Test
    void AC005_emailMalformadoResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "10000005", "no-es-un-email", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.details[0].campo").value("email"));
    }

    @Test
    void AC006_nombreOApellidoVaciosResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("", "Perez", "10000006", "ac006a@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].campo").value("nombre"));
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "  ", "10000007", "ac006b@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].campo").value("apellido"));
    }

    @Test
    void AC007_altaSinTelefonoEsValida() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Maria", "Gomez", "10000008", "ac007@example.com", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.telefono").doesNotExist());
    }

    @Test
    void AC008_sinTokenResponde401ConEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "10000009", "ac008@example.com", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.message").value("Token ausente o inválido"));
    }

    @Test
    void AC009_tokenClienteResponde403() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenCliente(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan", "Perez", "10000010", "ac009@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    // --- Consulta (GET /{id}) ---

    @Test
    void AC010_adminConsultaCualquierClienteYRecibe200() throws Exception {
        long id = crearClienteAdmin("Ana", "Lopez", "10000011", "ac010@example.com", null);
        mockMvc.perform(get("/api/v1/clientes/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.dni").value("10000011"));
    }

    @Test
    void AC011_clienteConsultaSuPropioIdYRecibe200() throws Exception {
        long id = crearClienteAdmin("Ana", "Lopez", "10000012", "ac011@example.com", null);
        mockMvc.perform(get("/api/v1/clientes/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenCliente(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void AC012_clienteConsultaIdAjenoYRecibe403() throws Exception {
        long id = crearClienteAdmin("Ana", "Lopez", "10000013", "ac012@example.com", null);
        mockMvc.perform(get("/api/v1/clientes/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenCliente(999999L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC013_consultarIdInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/clientes/{id}", 999999)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENTE_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.message").value("Cliente no encontrado"));
    }

    // --- Edición (PUT) ---

    @Test
    void AC014_adminEditaConDatosValidosYRecibe200ConRepresentacionActualizada() throws Exception {
        long id = crearClienteAdmin("Juan", "Perez", "10000014", "ac014@example.com", null);
        mockMvc.perform(put("/api/v1/clientes/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Maria", "Gomez", "20000014", "ac014-nuevo@example.com", "114567890")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Maria"))
                .andExpect(jsonPath("$.apellido").value("Gomez"))
                .andExpect(jsonPath("$.dni").value("20000014"))
                .andExpect(jsonPath("$.email").value("ac014-nuevo@example.com"))
                .andExpect(jsonPath("$.telefono").value("114567890"))
                .andExpect(jsonPath("$.fechaAlta").isNotEmpty());
    }

    @Test
    void AC015_editarIdInexistenteResponde404() throws Exception {
        mockMvc.perform(put("/api/v1/clientes/{id}", 999999)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Maria", "Gomez", "10000015", "ac015@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void AC016_edicionQueProduceDniDuplicadoResponde409() throws Exception {
        // "a" ocupa el dni 10000016; "b" intentará adoptarlo → 409.
        crearClienteAdmin("Ana", "Lopez", "10000016", "ac016a@example.com", null);
        long b = crearClienteAdmin("Bruno", "Diaz", "20000016", "ac016b@example.com", null);
        mockMvc.perform(put("/api/v1/clientes/{id}", b)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Bruno", "Diaz", "10000016", "ac016b@example.com", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICTO_UNICIDAD"))
                .andExpect(jsonPath("$.details[0].campo").value("dni"));
    }

    @Test
    void AC017_edicionQueProduceEmailDuplicadoResponde409() throws Exception {
        // "a" ocupa el email ac017@example.com; "b" intentará adoptarlo → 409.
        crearClienteAdmin("Ana", "Lopez", "10000017", "ac017@example.com", null);
        long b = crearClienteAdmin("Bruno", "Diaz", "20000017", "ac017b@example.com", null);
        mockMvc.perform(put("/api/v1/clientes/{id}", b)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Bruno", "Diaz", "20000017", "ac017@example.com", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].campo").value("email"));
    }

    @Test
    void AC018_edicionQueMantienePropioDniYEmailEsValida() throws Exception {
        long id = crearClienteAdmin("Juan", "Perez", "10000018", "ac018@example.com", null);
        mockMvc.perform(put("/api/v1/clientes/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Juan Carlos", "Perez", "10000018", "ac018@example.com", "+549112345678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Carlos"))
                .andExpect(jsonPath("$.dni").value("10000018"))
                .andExpect(jsonPath("$.email").value("ac018@example.com"));
    }

    @Test
    void AC019_edicionConTokenClienteResponde403() throws Exception {
        mockMvc.perform(put("/api/v1/clientes/{id}", 1L)
                        .header("Authorization", "Bearer " + tokens.tokenCliente(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCliente("Maria", "Gomez", "10000019", "ac019@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    // --- Listado (GET /api/v1/clientes) ---

    @Test
    void AC020_listadoConTokenAdminResponde200ConListaOrdenadaPorIdAsc() throws Exception {
        crearClienteAdmin("Ana", "Lopez", "10000020", "ac020@example.com", null);
        crearClienteAdmin("Bruno", "Diaz", "20000020", "ac020b@example.com", null);
        // Persistencia real: los ids asignados son crecientes y el listado los
        // devuelve en ese orden.
        mockMvc.perform(get("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].id", greaterThan(0)))
                .andExpect(result -> {
                    JsonNode array = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
                    for (int i = 0; i < array.size() - 1; i++) {
                        long actual = array.get(i).get("id").asLong();
                        long siguiente = array.get(i + 1).get("id").asLong();
                        if (actual >= siguiente) {
                            throw new AssertionError(
                                    "Lista no ordenada por id ascendente en posición " + i);
                        }
                    }
                });
    }

    @Test
    void AC021_listadoConTokenClienteResponde403() throws Exception {
        mockMvc.perform(get("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenCliente(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
    }

    @Test
    void AC022_listadoSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.message").value("Token ausente o inválido"));
    }

    // --- Persistencia real y envelope (AC-024) ---

    @Test
    void AC024_losDatosPersistenEntreRequests() throws Exception {
        long id = crearClienteAdmin("Persistente", "Cliente", "10000024", "ac024@example.com", "114567890");
        mockMvc.perform(get("/api/v1/clientes/{id}", id)
                        .header("Authorization", "Bearer " + tokens.tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Persistente"))
                .andExpect(jsonPath("$.dni").value("10000024"));
    }

    @Test
    void AC024_jsonMalformadoResponde400ConEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no-es-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void AC024_idNoNumericoResponde400ConEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clientes/abc")
                        .header("Authorization", "Bearer " + tokens.tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
    }
}
