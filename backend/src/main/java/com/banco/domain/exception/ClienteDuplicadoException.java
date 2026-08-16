package com.banco.domain.exception;

/**
 * Conflicto de unicidad de dni o email (BR-001, BR-003; ERR-001) → 409.
 * {@code campo} es "dni" o "email".
 */
public class ClienteDuplicadoException extends RuntimeException {

    private final String campo;
    private final String valor;

    public ClienteDuplicadoException(String campo, String valor) {
        super(mensaje(campo));
        this.campo = campo;
        this.valor = valor;
    }

    private static String mensaje(String campo) {
        return "dni".equals(campo)
                ? "Ya existe un cliente con ese DNI"
                : "Ya existe un cliente con ese email";
    }

    public String getCampo() {
        return campo;
    }

    public String getValor() {
        return valor;
    }
}
