package com.banco.domain;

import com.banco.domain.exception.MoneyInvalidoException;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VO Money (BR-002, AC-026): monto nunca negativo; {@code cero(Moneda)}.
 */
class MoneyTest {

    private static final Moneda ARS = new Moneda("ARS");

    @Test
    void montoCeroEsValido() {
        Money money = new Money(BigDecimal.ZERO, ARS);

        assertEquals(0, money.monto().signum());
        assertEquals(ARS, money.moneda());
    }

    @Test
    void montoPositivoEsValido() {
        Money money = new Money(new BigDecimal("100.50"), ARS);

        assertEquals(new BigDecimal("100.50"), money.monto());
    }

    @Test
    void montoNegativoLanzaMoneyInvalido() {
        assertThrows(MoneyInvalidoException.class, () -> new Money(new BigDecimal("-1"), ARS));
    }

    @Test
    void montoNullLanzaMoneyInvalido() {
        assertThrows(MoneyInvalidoException.class, () -> new Money(null, ARS));
    }

    @Test
    void monedaNullLanzaMoneyInvalido() {
        assertThrows(MoneyInvalidoException.class, () -> new Money(BigDecimal.ZERO, null));
    }

    @Test
    void ceroCreaMontoCeroConLaMonedaDada() {
        Money cero = Money.cero(ARS);

        assertEquals(BigDecimal.ZERO, cero.monto());
        assertEquals(ARS, cero.moneda());
    }
}
