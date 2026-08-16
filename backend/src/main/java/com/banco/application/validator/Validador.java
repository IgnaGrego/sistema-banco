package com.banco.application.validator;

import com.banco.domain.exception.DatosInvalidosException;

/**
 * Contrato de cada eslabón de la cadena de responsabilidad (CoR).
 */
@FunctionalInterface
public interface Validador {

    void validar(DatosCliente datos) throws DatosInvalidosException;
}
