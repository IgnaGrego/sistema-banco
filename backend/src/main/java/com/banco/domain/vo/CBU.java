package com.banco.domain.vo;

import com.banco.domain.exception.CbuInvalidoException;

/**
 * Value object del CBU (BR-001): exactamente 22 dígitos numéricos
 * (8 banco + 4 sucursal + 10 cuenta — A-002). La regla vive en el VO
 * (como {@code DNI}).
 */
public record CBU(String valor) {

    public CBU {
        if (valor == null || !valor.matches("^[0-9]{22}$")) {
            throw new CbuInvalidoException();
        }
    }
}
