package com.banco.application.command;

/**
 * Entrada del login (FR-002).
 */
public record LoginCommand(String username, String password) {
}
