package com.banco.application;

import com.banco.application.validator.AperturaValidator;
import com.banco.domain.exception.DatosInvalidosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validación de forma de la apertura (ERR-001, AC-004 lógica): tipo parseable
 * y moneda con formato {@code ^[A-Z]{3}$}; cortocircuito ante el primer error.
 */
class AperturaValidatorTest {

    private final AperturaValidator validator = new AperturaValidator();

    @Test
    void tipoNullLanzaDatosInvalidosConCampoTipo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(null, null));
        assertEquals("tipo", e.getCampo());
    }

    @Test
    void tipoBlankLanzaDatosInvalidosConCampoTipo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar("  ", "ARS"));
        assertEquals("tipo", e.getCampo());
    }

    @Test
    void tipoNoParseableLanzaDatosInvalidosConCampoTipo() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar("AHORRO", null));
        assertEquals("tipo", e.getCampo());
    }

    @Test
    void tipoEnMinusculasOLowerSnakeLanzaDatosInvalidosConCampoTipo() {
        assertThrows(DatosInvalidosException.class, () -> validator.validar("caja_ahorro", null));
        assertEquals("tipo", assertThrows(DatosInvalidosException.class,
                () -> validator.validar("cuenta_corriente", null)).getCampo());
    }

    @Test
    void monedaConFormatoInvalidoLanzaDatosInvalidosConCampoMoneda() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar("CAJA_AHORRO", "ar"));
        assertEquals("moneda", e.getCampo());
    }

    @Test
    void monedaEnMinusculasLanzaDatosInvalidosConCampoMoneda() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar("CAJA_AHORRO", "ars"));
        assertEquals("moneda", e.getCampo());
    }

    @Test
    void monedaConEspacioLanzaDatosInvalidosConCampoMoneda() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar("CAJA_AHORRO", "AR S"));
        assertEquals("moneda", e.getCampo());
    }

    @Test
    void tipoYMonedaValidosNoLanzan() {
        assertDoesNotThrow(() -> validator.validar("CAJA_AHORRO", "ARS"));
        assertDoesNotThrow(() -> validator.validar("CUENTA_CORRIENTE", "ARS"));
        assertDoesNotThrow(() -> validator.validar("CAJA_AHORRO", null));
        assertDoesNotThrow(() -> validator.validar("CAJA_AHORRO", "  "));
    }

    @Test
    void cortocircuitoPrimerErrorGana() {
        // tipo inválido Y moneda inválida → gana "tipo" (se corta).
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> validator.validar("AHORRO", "ar"));
        assertEquals("tipo", e.getCampo());
    }
}
