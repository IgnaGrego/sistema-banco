package com.banco.domain.exception;

/**
 * Invariante interno del VO {@code Money} (BR-002): monto null, moneda null o
 * monto negativo. Sin mapeo en el handler: inalcanzable desde entradas de
 * usuario en este sprint (solo la factory construye {@code Money}, siempre con
 * 0); si ocurriera, cae en el fallback 500 (ver docs/architecture/SPEC-002.md
 * §8.5).
 */
public class MoneyInvalidoException extends RuntimeException {

    public MoneyInvalidoException() {
        super("El monto no es válido");
    }
}
