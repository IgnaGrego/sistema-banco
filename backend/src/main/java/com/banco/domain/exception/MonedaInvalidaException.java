package com.banco.domain.exception;

/**
 * Formato de moneda inválido: no cumple {@code ^[A-Z]{3}$}. La lanza el VO
 * {@code Moneda} (defensivo; la validación de forma de la apertura la traduce
 * a {@link DatosInvalidosException} con campo "moneda").
 */
public class MonedaInvalidaException extends RuntimeException {

    public MonedaInvalidaException() {
        super("Formato de moneda inválido");
    }
}
