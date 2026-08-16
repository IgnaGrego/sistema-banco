package com.banco.domain;

import com.banco.domain.exception.MonedaInvalidaException;
import com.banco.domain.vo.Moneda;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VO Moneda (validación defensiva del formato {@code ^[A-Z]{3}$}); el 422 de
 * "moneda no soportada" (USD) se prueba en la capa de aplicación.
 */
class MonedaTest {

    @Test
    void codigosValidos() {
        assertEquals("ARS", new Moneda("ARS").codigo());
        assertEquals("USD", new Moneda("USD").codigo());
    }

    @Test
    void codigoEnMinusculasLanzaMonedaInvalida() {
        assertThrows(MonedaInvalidaException.class, () -> new Moneda("ars"));
    }

    @Test
    void codigoDeDosLetrasLanzaMonedaInvalida() {
        assertThrows(MonedaInvalidaException.class, () -> new Moneda("AR"));
    }

    @Test
    void codigoConDigitoLanzaMonedaInvalida() {
        assertThrows(MonedaInvalidaException.class, () -> new Moneda("abc1"));
    }

    @Test
    void codigoNullLanzaMonedaInvalida() {
        assertThrows(MonedaInvalidaException.class, () -> new Moneda(null));
    }
}
