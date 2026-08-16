package com.banco.infrastructure.adapter.web;

/**
 * Entrada del registro (POST /api/v1/auth/register). Record plano sin
 * anotaciones de validación: las reglas de negocio (BR-002, BR-003, A-005,
 * ERR-007) se validan en RegistroValidator (application). {@code clienteId}
 * solo aplica para rol CLIENTE (A-003).
 */
public record RegistrarUsuarioRequest(String username, String password, String rol, Long clienteId) {
}
