package com.banco.domain.exception;

/**
 * Saldo insuficiente en la cuenta origen (ERR-001 → 422 SALDO_INSUFICIENTE,
 * BR-001). Invariante del agregado {@code Cuenta}: el saldo nunca queda
 * negativo.
 */
public class SaldoInsuficienteException extends RuntimeException {

    public SaldoInsuficienteException() {
        super("Saldo insuficiente");
    }
}
