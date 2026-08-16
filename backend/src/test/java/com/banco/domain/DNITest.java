package com.banco.domain;

import com.banco.domain.exception.DniInvalidoException;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VO DNI (BR-002): solo dígitos, entre 7 y 8 caracteres (AC-004, AC-023).
 */
class DNITest {

    @Test
    void dniDe7DigitosEsValido() {
        DNI dni = new DNI("1234567");
        assertEquals("1234567", dni.valor());
    }

    @Test
    void dniDe8DigitosEsValido() {
        DNI dni = new DNI("12345678");
        assertEquals("12345678", dni.valor());
    }

    @Test
    void dniNoNumericoLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI("abcdefg"));
    }

    @Test
    void dniDe6DigitosLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI("123456"));
    }

    @Test
    void dniDe9DigitosLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI("123456789"));
    }

    @Test
    void dniConCaracteresMezcladosLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI("12345ab"));
    }

    @Test
    void dniNullLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI(null));
    }

    @Test
    void dniVacioLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI(""));
    }

    @Test
    void dniConEspaciosLanzaDniInvalido() {
        assertThrows(DniInvalidoException.class, () -> new DNI("1234 567"));
    }
}
