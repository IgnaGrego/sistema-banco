package com.banco.domain.exception;

/**
 * Cuenta origen/destino inexistente (ERR-002 → 404 CUENTA_NO_ENCONTRADA).
 */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException() {
        super("Cuenta no encontrada");
    }
}
