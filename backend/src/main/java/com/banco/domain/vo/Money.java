package com.banco.domain.vo;

import com.banco.domain.exception.MoneyInvalidoException;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Value object monetario inmutable (BR-002): saldo nunca negativo. Las
 * operaciones aritméticas (SPEC-004/005) usan {@link MathContext#DECIMAL128}
 * sobre {@link BigDecimal} — nunca {@code double} (ARCHITECTURE.md §6); las
 * comparaciones usan {@code compareTo} (tolerantes a la escala).
 *
 * <p>La escala ≤ 2 del monto de una transferencia (BR-003 de SPEC-004) la
 * verifica el {@code TransferValidator}, no este VO (decisión de SPEC-002:
 * el VO solo valida monto ≥ 0).
 */
public record Money(BigDecimal monto, Moneda moneda) {

    public Money {
        if (monto == null || moneda == null || monto.signum() < 0) {
            throw new MoneyInvalidoException();
        }
    }

    /**
     * Cero en la moneda dada (saldo inicial de toda cuenta — FR-003).
     */
    public static Money cero(Moneda moneda) {
        return new Money(BigDecimal.ZERO, moneda);
    }

    /**
     * Factory de montos en ARS (moneda del MVP de SPEC-004 — BR-003).
     */
    public static Money ars(BigDecimal monto) {
        return new Money(monto, new Moneda("ARS"));
    }

    /**
     * Suma (misma moneda; el flujo de transferencia valida la compatibilidad
     * antes — BR-007).
     */
    public Money sumar(Money otro) {
        return new Money(monto.add(otro.monto, MathContext.DECIMAL128), moneda);
    }

    /**
     * Resta (mismo invariante monto ≥ 0 del constructor: si {@code otro} es
     * mayor que {@code this} el resultado quedaría negativo y el VO lanza
     * {@link MoneyInvalidoException}; el dominio valida antes con
     * {@link #esMayorQue}).
     */
    public Money restar(Money otro) {
        return new Money(monto.subtract(otro.monto, MathContext.DECIMAL128), moneda);
    }

    /**
     * {@code this > otro} por {@code compareTo} (tolerante a la escala).
     */
    public boolean esMayorQue(Money otro) {
        return monto.compareTo(otro.monto) > 0;
    }

    /**
     * {@code this >= otro} por {@code compareTo} (tolerante a la escala).
     */
    public boolean esMayorOIgualQue(Money otro) {
        return monto.compareTo(otro.monto) >= 0;
    }

    /**
     * {@code true} si el monto es cero (por {@code signum}).
     */
    public boolean esCero() {
        return monto.signum() == 0;
    }
}
