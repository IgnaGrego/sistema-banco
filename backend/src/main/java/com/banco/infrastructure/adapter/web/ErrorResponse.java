package com.banco.infrastructure.adapter.web;

import java.util.List;

/**
 * Envelope JSON estándar de error {@code { code, message, details? }}
 * (ARCHITECTURE.md §7). {@code details} se omite cuando es null
 * (spring.jackson.default-property-inclusion: non_null).
 */
public record ErrorResponse(String code, String message, List<DetalleError> details) {
}
