package com.banco.application.validator;

/**
 * Entrada del {@link RegistroValidator}. Normaliza (trim null-safe) el
 * username en un solo lugar (A-005: "no vacío tras recortar espacios"). La
 * password NO se recorta (nunca se altera la password).
 */
public record DatosRegistro(String username, String password, String rol, Long clienteId) {

    public DatosRegistro {
        username = trim(username);
    }

    private static String trim(String valor) {
        return valor == null ? null : valor.trim();
    }
}
