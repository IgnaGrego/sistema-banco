package com.banco.infrastructure.adapter.web;

/**
 * Entrada de {@code details} del envelope de error (campo de error).
 */
public record DetalleError(String campo, String mensaje) {
}
