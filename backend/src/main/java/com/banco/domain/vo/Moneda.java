package com.banco.domain.vo;

import java.util.Currency;

/**
 * Monedas soportadas (MVP: solo {@code ARS} — BR-007, SPEC-004 §12).
 * Expone la {@link Currency} ISO correspondiente para {@link Money}.
 */
public enum Moneda {

    ARS;

    public Currency currency() {
        return Currency.getInstance(name());
    }
}
