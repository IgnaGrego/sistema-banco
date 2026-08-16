package com.banco.application;

import com.banco.application.validator.CamposObligatoriosValidador;
import com.banco.application.validator.ClienteValidator;
import com.banco.application.validator.DatosCliente;
import com.banco.application.validator.FormatoValidador;
import com.banco.application.validator.LongitudValidador;
import com.banco.domain.exception.DatosInvalidosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cadena de responsabilidad ClienteValidator: obligatorios → longitudes →
 * formatos, con cortocircuito ante el primer error (AC-004..AC-007, AC-023).
 */
class ClienteValidatorTest {

    private ClienteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClienteValidator(
                new CamposObligatoriosValidador(),
                new LongitudValidador(),
                new FormatoValidador());
    }

    private static DatosCliente datosValidos() {
        return new DatosCliente("Juan", "Perez", "12345678", "juan@example.com", "+549112345678");
    }

    // --- Obligatorios (BR-004) ---

    @Test
    void datosValidosPasanLaValidacion() {
        assertDoesNotThrow(() -> validator.validar(datosValidos()));
    }

    @Test
    void datosValidosSinTelefonoPasanLaValidacion() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", "juan@example.com", null);
        assertDoesNotThrow(() -> validator.validar(datos));
    }

    @Test
    void nombreNullReportaCampoNombre() {
        DatosCliente datos = new DatosCliente(null, "Perez", "12345678", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("nombre", e.getCampo());
    }

    @Test
    void nombreBlankTrasTrimReportaCampoNombre() {
        DatosCliente datos = new DatosCliente("   ", "Perez", "12345678", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("nombre", e.getCampo());
    }

    @Test
    void apellidoVacioReportaCampoApellido() {
        DatosCliente datos = new DatosCliente("Juan", "", "12345678", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("apellido", e.getCampo());
    }

    @Test
    void emailNullReportaCampoEmail() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", null, null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("email", e.getCampo());
    }

    @Test
    void dniVacioReportaCampoDni() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "  ", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("dni", e.getCampo());
    }

    // --- Longitudes (BR-004, BR-003, A-005) ---

    @Test
    void nombreDe101CaracteresReportaCampoNombre() {
        DatosCliente datos = new DatosCliente("a".repeat(101), "Perez", "12345678", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("nombre", e.getCampo());
    }

    @Test
    void apellidoDe101CaracteresReportaCampoApellido() {
        DatosCliente datos = new DatosCliente("Juan", "b".repeat(101), "12345678", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("apellido", e.getCampo());
    }

    @Test
    void emailDe255CaracteresReportaCampoEmail() {
        String emailLargo = "a".repeat(245) + "@example.com"; // 245 + 10 = 255
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", emailLargo, null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("email", e.getCampo());
    }

    @Test
    void nombreDe100CaracteresEsValido() {
        DatosCliente datos = new DatosCliente("a".repeat(100), "Perez", "12345678", "juan@example.com", null);
        assertDoesNotThrow(() -> validator.validar(datos));
    }

    // --- Formatos (BR-003, BR-005, BR-002) ---

    @Test
    void emailMalformadoReportaCampoEmail() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", "juan@", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("email", e.getCampo());
    }

    @Test
    void emailSinDominioValidoReportaCampoEmail() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", "juan@example", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("email", e.getCampo());
    }

    @Test
    void telefonoMalformadoReportaCampoTelefono() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", "juan@example.com", "abc");
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("telefono", e.getCampo());
    }

    @Test
    void telefonoConMasDe15DigitosReportaCampoTelefono() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", "juan@example.com", "1234567890123456");
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("telefono", e.getCampo());
    }

    @Test
    void telefonoConMasInicialEsValido() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345678", "juan@example.com", "+5491123456789");
        assertDoesNotThrow(() -> validator.validar(datos));
    }

    @Test
    void dniInvalidoReportaCampoDniConMensajeDelVo() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "12345", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("dni", e.getCampo());
        assertEquals("El DNI debe contener entre 7 y 8 dígitos", e.getMensaje());
    }

    @Test
    void dniNoNumericoReportaCampoDni() {
        DatosCliente datos = new DatosCliente("Juan", "Perez", "abcdefg", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("dni", e.getCampo());
    }

    // --- Cortocircuito y trim ---

    @Test
    void cortaAnteElPrimerError() {
        // nombre vacío (eslabón 1) + dni inválido (eslabón 3): gana el primero.
        DatosCliente datos = new DatosCliente("", "Perez", "12345", "juan@example.com", null);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> validator.validar(datos));
        assertEquals("nombre", e.getCampo());
    }

    @Test
    void datosClienteRecortaEspaciosDeNombreApellidoYEmail() {
        DatosCliente datos = new DatosCliente("  Juan  ", " Perez ", "12345678", "  juan@example.com ", null);
        assertEquals("Juan", datos.nombre());
        assertEquals("Perez", datos.apellido());
        assertEquals("juan@example.com", datos.email());
    }
}
