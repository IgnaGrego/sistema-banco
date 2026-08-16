package com.banco.application.validator;

import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.DniInvalidoException;
import com.banco.domain.vo.DNI;

import java.util.regex.Pattern;

/**
 * Eslabón 3 de la CoR: formatos — email (BR-003), telefono opcional (BR-005) y
 * dni vía el VO {@link DNI} (BR-002; la regla vive en el VO, este validador la
 * expone como error de campo).
 */
public class FormatoValidador implements Validador {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9]{6,15}$");

    @Override
    public void validar(DatosCliente datos) {
        validarEmail(datos.email());
        validarTelefono(datos.telefono());
        validarDni(datos.dni());
    }

    private void validarEmail(String email) {
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new DatosInvalidosException("email", "El email no tiene un formato válido");
        }
    }

    private void validarTelefono(String telefono) {
        if (telefono != null && !telefono.isBlank() && !TELEFONO.matcher(telefono).matches()) {
            throw new DatosInvalidosException("telefono", "El teléfono no tiene un formato válido");
        }
    }

    private void validarDni(String dni) {
        if (dni == null || dni.isBlank()) {
            return; // obligatoriedad ya validada por el eslabón 1
        }
        try {
            new DNI(dni);
        } catch (DniInvalidoException e) {
            throw new DatosInvalidosException("dni", e.getMessage());
        }
    }
}
