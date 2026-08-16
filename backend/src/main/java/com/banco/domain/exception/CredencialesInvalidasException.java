package com.banco.domain.exception;

/**
 * Credenciales inválidas en login (ERR-001) → 401. El mensaje es idéntico para
 * username inexistente y password incorrecta (A-004: no se revela qué dato
 * falló — no enumeración de usuarios).
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales inválidas");
    }
}
