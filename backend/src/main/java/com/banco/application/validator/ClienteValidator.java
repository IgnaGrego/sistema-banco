package com.banco.application.validator;

import java.util.List;

/**
 * Orquesta la cadena de responsabilidad (CoR): mantiene la lista de
 * validadores en orden (obligatorios → longitudes → formatos) y corta ante la
 * primera {@link com.banco.domain.exception.DatosInvalidosException}.
 */
public class ClienteValidator {

    private final List<Validador> validadores;

    public ClienteValidator(List<Validador> validadores) {
        this.validadores = List.copyOf(validadores);
    }

    public ClienteValidator(Validador... validadores) {
        this(List.of(validadores));
    }

    public void validar(DatosCliente datos) {
        for (Validador validador : validadores) {
            // La excepción se propaga: corta el recorrido ante el primer error.
            validador.validar(datos);
        }
    }
}
