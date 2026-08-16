package com.banco.domain;

import com.banco.domain.exception.MoneyInvalidoException;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VO Money (BR-002, AC-026): monto nunca negativo; {@code cero(Moneda)}.
 * Operaciones de SPEC-004 (ADR-007): {@code sumar}/{@code restar} con
 * {@code MathContext.DECIMAL128}, comparaciones por {@code compareTo}
 * (tolerantes a la escala), {@code esCero} y la factory {@code ars} (AC-021).
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

    // --- Operaciones de SPEC-004 (ADR-007) ---

    @Test
    void arsCreaMontoConMonedaArs() {
        Money money = Money.ars(new BigDecimal("15000.00"));

        assertEquals(new BigDecimal("15000.00"), money.monto());
        assertEquals(new Moneda("ARS"), money.moneda());
        assertEquals("ARS", money.moneda().codigo());
    }

    @Test
    void sumarDevuelveLaSumaConLaMismaMoneda() {
        Money a = Money.ars(new BigDecimal("100.00"));
        Money b = Money.ars(new BigDecimal("50.25"));

        Money suma = a.sumar(b);

        assertEquals(new BigDecimal("150.25"), suma.monto());
        assertEquals(new Moneda("ARS"), suma.moneda());
    }

    @Test
    void sumarConEscalaDiferenteUsaDecimal128YNoPierdePrecision() {
        // MathContext.DECIMAL128 + BigDecimal.add (sin double): 0.1 + 0.2 no
        // puede dar 0.30000000000000004 (error de punto flotante).
        Money a = Money.ars(new BigDecimal("0.10"));
        Money b = Money.ars(new BigDecimal("0.20"));

        Money suma = a.sumar(b);

        assertEquals(0, suma.monto().compareTo(new BigDecimal("0.30")));
    }

    @Test
    void restarDevuelveLaDiferencia() {
        Money a = Money.ars(new BigDecimal("150.00"));
        Money b = Money.ars(new BigDecimal("50.00"));

        Money resta = a.restar(b);

        assertEquals(new BigDecimal("100.00"), resta.monto());
        assertEquals(new Moneda("ARS"), resta.moneda());
    }

    @Test
    void esMayorQueComparaPorCompareToToleranteALaEscala() {
        Money cien = Money.ars(new BigDecimal("100.00"));
        Money cienSinEscala = Money.ars(new BigDecimal("100"));
        Money doscientos = Money.ars(new BigDecimal("200.00"));

        assertTrue(doscientos.esMayorQue(cien));
        assertFalse(cien.esMayorQue(doscientos));
        // 100.00 vs 100: compareTo devuelve 0 (escala distinta, mismo valor).
        assertFalse(cien.esMayorQue(cienSinEscala));
    }

    @Test
    void esMayorOIgualQueIncluyeLaIgualdad() {
        Money cien = Money.ars(new BigDecimal("100.00"));
        Money cienSinEscala = Money.ars(new BigDecimal("100"));
        Money doscientos = Money.ars(new BigDecimal("200.00"));

        assertTrue(cien.esMayorOIgualQue(cienSinEscala));
        assertTrue(doscientos.esMayorOIgualQue(cien));
        assertFalse(cien.esMayorOIgualQue(doscientos));
    }

    @Test
    void esCeroDetectaMontoCero() {
        assertTrue(Money.ars(BigDecimal.ZERO).esCero());
        assertFalse(Money.ars(new BigDecimal("0.01")).esCero());
    }

    @Test
    void restarUnMontoMayorLanzaMoneyInvalido() {
        // Invariante del VO: el saldo nunca queda negativo (BR-001). El
        // dominio valida antes con esMayorQue; el VO es la última barrera.
        Money diez = Money.ars(new BigDecimal("10.00"));
        Money veinte = Money.ars(new BigDecimal("20.00"));

        assertThrows(MoneyInvalidoException.class, () -> diez.restar(veinte));
    }
}
