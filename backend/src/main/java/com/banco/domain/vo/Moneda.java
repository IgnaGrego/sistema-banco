package com.banco.domain.vo;

import com.banco.domain.exception.MonedaInvalidaException;

/**
 * Value object de moneda (código ISO 4217 alpha-3). Record con {@code String}
 * (no enum): distingue "formato inválido" (400, {@link MonedaInvalidaException})
 * de "moneda no soportada" (422, p. ej. USD — decisión de soporte en la capa
 * de aplicación).
 */
public record Moneda(String codigo) {

    public Moneda {
        if (codigo == null || !codigo.matches("^[A-Z]{3}$")) {
            throw new MonedaInvalidaException();
        }
    }
}
