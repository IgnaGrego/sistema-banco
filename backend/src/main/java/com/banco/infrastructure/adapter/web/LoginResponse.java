package com.banco.infrastructure.adapter.web;

/**
 * Salida del login (FR-002: "responde 200 OK con el token").
 */
public record LoginResponse(String token) {
}
