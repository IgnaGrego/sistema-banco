package com.banco.domain.exception;

/**
 * Moneda no soportada (ERR-009) → 422 (BR-005, A-005): el banco opera solo
 * {@code ARS} en este sprint. La lanza {@code AbrirCuentaUseCase} cuando la
 * moneda tiene formato válido pero no es {@code ARS} (p. ej. {@code USD}).
 */
public class MonedaNoSoportadaException extends RuntimeException {

    public MonedaNoSoportadaException() {
        super("Moneda no soportada");
    }
}
