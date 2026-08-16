package com.banco.domain.exception;

/**
 * Username duplicado en registro (ERR-005) → 409 indicando el campo (BR-003;
 * misma semántica que SPEC-001 ERR-001/ClienteDuplicadoException).
 */
public class UsernameDuplicadoException extends RuntimeException {

    private final String campo;

    public UsernameDuplicadoException() {
        super("Ya existe un usuario con ese username");
        this.campo = "username";
    }

    public String getCampo() {
        return campo;
    }
}
