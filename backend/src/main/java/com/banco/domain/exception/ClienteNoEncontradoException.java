package com.banco.domain.exception;

/**
 * Cliente inexistente (ERR-004) → 404.
 */
public class ClienteNoEncontradoException extends RuntimeException {

    public ClienteNoEncontradoException() {
        super("Cliente no encontrado");
    }
}
