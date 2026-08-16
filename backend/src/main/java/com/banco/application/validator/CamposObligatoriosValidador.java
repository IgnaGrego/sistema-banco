package com.banco.application.validator;

import com.banco.domain.exception.DatosInvalidosException;

/**
 * Eslabón 1 de la CoR: nombre, apellido, email y dni son obligatorios y no
 * blank tras el trim (BR-004 parcial).
 */
public class CamposObligatoriosValidador implements Validador {

    @Override
    public void validar(DatosCliente datos) {
        validarCampo("nombre", datos.nombre());
        validarCampo("apellido", datos.apellido());
        validarCampo("email", datos.email());
        validarCampo("dni", datos.dni());
    }

    private void validarCampo(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidosException(campo, "El campo " + campo + " es obligatorio");
        }
    }
}
