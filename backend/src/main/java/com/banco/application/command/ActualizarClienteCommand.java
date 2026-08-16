package com.banco.application.command;

/**
 * Entrada de la edición de cliente (PUT total, FR-003 / A-004).
 */
public record ActualizarClienteCommand(Long id, String nombre, String apellido,
                                       String dni, String email, String telefono) {
}
