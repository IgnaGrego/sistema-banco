package com.banco.application;

import com.banco.application.validator.AperturaValidator;
import com.banco.domain.exception.DatosInvalidosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validación de forma de la apertura (ERR-001, AC-004 lógica): clienteId
 * obligatorio, tipo parseable y moneda con formato {@code ^[A-Z]{3}$};
 * cortocircuito ante el primer error.
 */
class AperturaValidatorTest {

    private final AperturaValidator validator = new AperturaValidator();

    @Test
    void clienteIdNullLanzaDatosInvalidosConCampoClienteId() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(null, "CAJA_AHORRO", "ARS"));
        assertEquals("clienteId", e.getCampo());
    }

    @Test
    void tipoNullLanzaDatosInvalidosConCampoTipo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, null, null));
        assertEquals("tipo", e.getCampo());
    }

    @Test
    void tipoBlankLanzaDatosInvalidosConCampoTipo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, "  ", "ARS"));
        assertEquals("tipo", e.getCampo());
    }

    @Test
    void tipoNoParseableLanzaDatosInvalidosConCampoTipo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, "AHORRO", null));
        assertEquals("tipo", e.getCampo());
    }

    @Test
    void tipoEnMinusculasOLowerSnakeLanzaDatosInvalidosConCampoTipo() {
        assertThrows(DatosInvalidosException.class, () -> validator.validar(1L, "caja_ahorro", null));
        assertEquals("tipo", assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, "cuenta_corriente", null)).getCampo());
    }

    @Test
    void monedaConFormatoInvalidoLanzaDatosInvalidosConCampoMoneda() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, "CAJA_AHORRO", "ar"));
        assertEquals("moneda", e.getCampo());
    }

    @Test
    void monedaEnMinusculasLanzaDatosInvalidosConCampoMoneda() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, "CAJA_AHORRO", "ars"));
        assertEquals("moneda", e.getCampo());
    }

    @Test
    void monedaConEspacioLanzaDatosInvalidosConCampoMoneda() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(1L, "CAJA_AHORRO", "AR S"));
        assertEquals("moneda", e.getCampo());
    }

    @Test
    void clienteIdTipoYMonedaValidosNoLanzan() {
        assertDoesNotThrow(() -> validator.validar(1L, "CAJA_AHORRO", "ARS"));
        assertDoesNotThrow(() -> validator.validar(1L, "CUENTA_CORRIENTE", "ARS"));
        assertDoesNotThrow(() -> validator.validar(1L, "CAJA_AHORRO", null));
        assertDoesNotThrow(() -> validator.validar(1L, "CAJA_AHORRO", "  "));
    }

    @Test
    void cortocircuitoPrimerErrorGana() {
        // clienteId null + tipo inválido + moneda inválida → gana "clienteId"
        // (el primer chequeo corta).
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(null, "AHORRO", "ar"));
        assertEquals("clienteId", e.getCampo());
    }
}
