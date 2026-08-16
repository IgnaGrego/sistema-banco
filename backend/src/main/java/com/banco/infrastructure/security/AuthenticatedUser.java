package com.banco.infrastructure.security;

/**
 * Principal del SecurityContext. {@code clienteId} es null para tokens ADMIN.
 */
public record AuthenticatedUser(Long clienteId, String rol) {
}
