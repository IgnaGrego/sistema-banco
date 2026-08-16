package com.banco.application.command;

/**
 * Entrada del alta de cliente (FR-001).
 */
public record CrearClienteCommand(String nombre, String apellido, String dni,
                                  String email, String telefono) {
}
