package com.banco.application.command;

/**
 * Entrada del registro de usuario (FR-001). {@code rol} como String: el parsing
 * a {@code Rol} lo hace la validación (RegistroValidator).
 */
public record RegistrarUsuarioCommand(String username, String password, String rol, Long clienteId) {
}
