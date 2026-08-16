package com.banco.domain.exception;

/**
 * Datos inválidos (ERR-002) → 400 con detalle de campo. La lanza la cadena de
 * validación de la capa de aplicación (CoR).
 */
public class DatosInvalidosException extends RuntimeException {

    private final String campo;
    private final String mensaje;

    public DatosInvalidosException(String campo, String mensaje) {
        super("Datos inválidos");
        this.campo = campo;
        this.mensaje = mensaje;
    }

    public String getCampo() {
        return campo;
    }

    public String getMensaje() {
        return mensaje;
    }
}
