package com.banco.domain.exception;

/**
 * DNI inválido (BR-002): no cumple {@code ^[0-9]{7,8}$}.
 * Mapeada a 400 en el handler (defensivo; la cadena de validación la
 * traduce a {@link DatosInvalidosException} con campo "dni").
 */
public class DniInvalidoException extends RuntimeException {

    public DniInvalidoException() {
        super("El DNI debe contener entre 7 y 8 dígitos");
    }
}
