package com.banco.domain.vo;

import com.banco.domain.exception.DniInvalidoException;

/**
 * Value object del documento nacional de identidad (BR-002):
 * solo dígitos, entre 7 y 8 caracteres.
 */
public record DNI(String valor) {

    public DNI {
        if (valor == null || !valor.matches("^[0-9]{7,8}$")) {
            throw new DniInvalidoException();
        }
    }
}
