package com.banco.domain.exception;

/**
 * Operar una cuenta {@code BLOQUEADA} (BR-003, A-001) → 422. La lanza la
 * guarda interna del agregado {@code Cuenta} ante cualquier operación de
 * negocio sobre una cuenta bloqueada (incluido volver a {@code bloquear()}).
 */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException() {
        super("La cuenta está bloqueada");
    }
}
