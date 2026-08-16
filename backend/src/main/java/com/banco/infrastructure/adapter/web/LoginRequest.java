package com.banco.infrastructure.adapter.web;

/**
 * Entrada del login (POST /api/v1/auth/login). Record plano sin anotaciones de
 * validación: cualquier entrada no válida converge en el mismo 401 idéntico
 * (ERR-001, A-004) en AutenticarUsuarioUseCase.
 */
public record LoginRequest(String username, String password) {
}
