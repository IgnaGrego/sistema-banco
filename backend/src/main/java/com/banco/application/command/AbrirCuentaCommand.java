package com.banco.application.command;

/**
 * Entrada de la apertura de cuenta (FR-001). {@code moneda} es nullable
 * (default {@code ARS} — A-005); {@code tipo} se parsea en la validación.
 */
public record AbrirCuentaCommand(Long clienteId, String tipo, String moneda) {
}
