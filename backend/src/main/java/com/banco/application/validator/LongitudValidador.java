package com.banco.application.validator;

import com.banco.domain.exception.DatosInvalidosException;

/**
 * Eslabón 2 de la CoR: longitudes máximas — nombre/apellido ≤ 100, email ≤ 254
 * (BR-004, BR-003, A-005).
 */
public class LongitudValidador implements Validador {

    private static final int MAX_NOMBRE_APELLIDO = 100;
    private static final int MAX_EMAIL = 254;

    @Override
    public void validar(DatosCliente datos) {
        validarLongitud("nombre", datos.nombre(), MAX_NOMBRE_APELLIDO);
        validarLongitud("apellido", datos.apellido(), MAX_NOMBRE_APELLIDO);
        validarLongitud("email", datos.email(), MAX_EMAIL);
    }

    private void validarLongitud(String campo, String valor, int max) {
        if (valor != null && valor.length() > max) {
            throw new DatosInvalidosException(campo,
                    "El campo " + campo + " supera la longitud máxima de " + max + " caracteres");
        }
    }
}
