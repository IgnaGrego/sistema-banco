package com.banco.domain.exception;

/**
 * Cuenta en estado BLOQUEADA (ERR-003 → 422 CUENTA_BLOQUEADA, BR-002): una
 * cuenta bloqueada no participa en transferencias.
 */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException() {
        super("La cuenta está bloqueada");
    }
}
