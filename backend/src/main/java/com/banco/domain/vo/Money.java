package com.banco.domain.vo;

import com.banco.domain.exception.MoneyInvalidoException;

import java.math.BigDecimal;

/**
 * Value object monetario inmutable (BR-002): saldo nunca negativo. Sin
 * operaciones aritméticas en este sprint (llegan con SPEC-004/005).
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
}
