package com.banco.domain.exception;

/**
 * Cuenta inexistente (ERR-003) → 404.
 */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException() {
        super("Cuenta no encontrada");
    }
}
