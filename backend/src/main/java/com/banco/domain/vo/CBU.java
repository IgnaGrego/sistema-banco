package com.banco.domain.vo;

import com.banco.domain.exception.CbuInvalidoException;

/**
 * Value object del CBU (Clave Bancaria Uniforme): exactamente 22 dígitos
 * (8 de banco + 14 de cuenta, estándar real argentino). Valida en el
 * constructor (SPEC-004 §8.2); el formato de la spec se definió en
 * docs/architecture/SPEC-004.md §8.2 (la discrepancia con el ejemplo de
 * FR-001 se documenta en §12).
 */
public record CBU(String valor) {

    public CBU {
        if (valor == null || !valor.matches("^[0-9]{22}$")) {
            throw new CbuInvalidoException();
        }
    }
}
