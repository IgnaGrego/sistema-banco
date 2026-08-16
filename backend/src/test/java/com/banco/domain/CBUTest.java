package com.banco.domain;

import com.banco.domain.exception.CbuInvalidoException;
import com.banco.domain.vo.CBU;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VO CBU (BR-001, AC-025): exactamente 22 dígitos numéricos.
 */
class CBUTest {

    @Test
    void cbuValidoDe22Digitos() {
        CBU cbu = new CBU("0000000100010000000011");

        assertEquals("0000000100010000000011", cbu.valor());
    }

    @Test
    void cbuDe21DigitosLanzaCbuInvalido() {
        assertThrows(CbuInvalidoException.class, () -> new CBU("000000010001000000001"));
    }

    @Test
    void cbuDe23DigitosLanzaCbuInvalido() {
        assertThrows(CbuInvalidoException.class, () -> new CBU("00000001000100000000111"));
    }

    @Test
    void cbuNoNumericoLanzaCbuInvalido() {
        assertThrows(CbuInvalidoException.class, () -> new CBU("000000010001000000001a"));
    }

    @Test
    void cbuNullOVacioLanzaCbuInvalido() {
        assertThrows(CbuInvalidoException.class, () -> new CBU(null));
        assertThrows(CbuInvalidoException.class, () -> new CBU(""));
    }
}
