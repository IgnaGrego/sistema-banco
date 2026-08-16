package com.banco.application;

import com.banco.application.validator.DatosRegistro;
import com.banco.application.validator.RegistroValidator;
import com.banco.domain.exception.DatosInvalidosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RegistroValidator: username obligatorio y ≤ 50 (A-005), password ≥ 8 (BR-002,
 * ERR-004), rol ∈ {CLIENTE, ADMIN} y, para CLIENTE, clienteId obligatorio
 * (ERR-007). Corte ante el primer error y trim de username vía DatosRegistro
 * (AC-003, AC-005 lógica, AC-018).
 */
class RegistroValidatorTest {

    private RegistroValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RegistroValidator();
    }

    private static DatosRegistro datosValidos() {
        return new DatosRegistro("juan", "password123", "CLIENTE", 7L);
    }

    @Test
    void datosValidosPasanLaValidacion() {
        assertDoesNotThrow(() -> validator.validar(datosValidos()));
    }

    @Test
    void adminSinClienteIdPasaLaValidacion() {
        assertDoesNotThrow(() -> validator.validar(new DatosRegistro("admin", "password123", "ADMIN", null)));
    }

    @Test
    void clienteConClienteIdPasaLaValidacion() {
        assertDoesNotThrow(() -> validator.validar(new DatosRegistro("cliente", "password123", "CLIENTE", 1L)));
    }

    // --- Username (A-005) ---

    @Test
    void usernameNullReportaCampoUsername() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro(null, "password123", "ADMIN", null)));
        assertEquals("username", e.getCampo());
    }

    @Test
    void usernameBlankTrasTrimReportaCampoUsername() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("   ", "password123", "ADMIN", null)));
        assertEquals("username", e.getCampo());
    }

    @Test
    void usernameDe51CaracteresReportaCampoUsername() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("a".repeat(51), "password123", "ADMIN", null)));
        assertEquals("username", e.getCampo());
    }

    @Test
    void usernameDe50CaracteresEsValido() {
        assertDoesNotThrow(() -> validator.validar(new DatosRegistro("a".repeat(50), "password123", "ADMIN", null)));
    }

    // --- Password (BR-002, ERR-004) ---

    @Test
    void passwordDe7CaracteresReportaCampoPassword() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("juan", "1234567", "CLIENTE", 7L)));
        assertEquals("password", e.getCampo());
    }

    @Test
    void passwordNullReportaCampoPassword() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("juan", null, "CLIENTE", 7L)));
        assertEquals("password", e.getCampo());
    }

    @Test
    void passwordDe8CaracteresEsValida() {
        assertDoesNotThrow(() -> validator.validar(new DatosRegistro("juan", "12345678", "CLIENTE", 7L)));
    }

    // --- Rol ---

    @Test
    void rolInvalidoReportaCampoRol() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("juan", "password123", "GERENTE", null)));
        assertEquals("rol", e.getCampo());
    }

    @Test
    void rolNullReportaCampoRol() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("juan", "password123", null, null)));
        assertEquals("rol", e.getCampo());
    }

    @Test
    void rolEnMinusculasEsInvalido() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("juan", "password123", "cliente", 7L)));
        assertEquals("rol", e.getCampo());
    }

    // --- CLIENTE sin clienteId (ERR-007) ---

    @Test
    void clienteSinClienteIdReportaCampoClienteId() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("juan", "password123", "CLIENTE", null)));
        assertEquals("clienteId", e.getCampo());
    }

    // --- Corte ante el primer error y trim ---

    @Test
    void cortaAnteElPrimerError() {
        // username vacío (chequeo 1) + password corta (chequeo 2): gana el primero.
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(new DatosRegistro("", "123", "CLIENTE", null)));
        assertEquals("username", e.getCampo());
    }

    @Test
    void datosRegistroRecortaEspaciosDelUsername() {
        DatosRegistro datos = new DatosRegistro("  juan  ", "password123", "ADMIN", null);
        assertEquals("juan", datos.username());
    }

    @Test
    void datosRegistroNoRecortaLaPassword() {
        DatosRegistro datos = new DatosRegistro("juan", "  password123  ", "ADMIN", null);
        assertEquals("  password123  ", datos.password());
    }
}
