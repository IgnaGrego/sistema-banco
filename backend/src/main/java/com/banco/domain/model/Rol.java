package com.banco.domain.model;

/**
 * Rol de un {@link Usuario} (RBAC, ADR-003). Enum plano: el parsing desde
 * String lo hace {@code RegistroValidator} en la capa de aplicación
 * ({@code Rol.valueOf} capturando {@code IllegalArgumentException}); una vez
 * parseado, el tipo no admite valores inválidos (docs/architecture/SPEC-003.md
 * §8.1/§8.2).
 */
public enum Rol {
    CLIENTE,
    ADMIN
}
