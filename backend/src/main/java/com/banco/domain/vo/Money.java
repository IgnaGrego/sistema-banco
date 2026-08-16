package com.banco.domain.vo;

import com.banco.domain.exception.DatosInvalidosException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Currency;

/**
 * Value object monetario (ARCHITECTURE.md §4/§6): {@link BigDecimal} +
 * {@link Currency}, inmutable, operaciones con {@link MathContext#DECIMAL128}
 * y sin {@code double}. El constructor valida monto ≥ 0 y escala ≤ 2
 * (BR-003 parcial a nivel de VO; el {@code monto > 0} lo exige el validador —
 * docs/architecture/SPEC-004.md §8.2).
 */
public record Money(BigDecimal monto, Currency moneda) {

    public Money {
        if (monto == null) {
            throw new DatosInvalidosException("monto", "El monto es obligatorio");
        }
        if (moneda == null) {
            throw new DatosInvalidosException("monto", "La moneda es obligatoria");
        }
        if (monto.signum() < 0) {
            throw new DatosInvalidosException("monto", "El monto no puede ser negativo");
        }
        if (monto.scale() > 2) {
            throw new DatosInvalidosException("monto", "El monto no puede tener más de 2 decimales");
        }
    }

    /**
     * Factory para la moneda del MVP (ARS — BR-007).
     */
    public static Money ars(BigDecimal monto) {
        return new Money(monto, Moneda.ARS.currency());
    }

    public Money sumar(Money otro) {
        return new Money(monto.add(otro.monto, MathContext.DECIMAL128), moneda);
    }

    public Money restar(Money otro) {
        return new Money(monto.subtract(otro.monto, MathContext.DECIMAL128), moneda);
    }

    public boolean esMayorQue(Money otro) {
        return monto.compareTo(otro.monto) > 0;
    }

    public boolean esMayorOIgualQue(Money otro) {
        return monto.compareTo(otro.monto) >= 0;
    }

    public boolean esCero() {
        return monto.signum() == 0;
    }
}
