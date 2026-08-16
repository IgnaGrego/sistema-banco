package com.banco.domain.model;

/**
 * Estado de la cuenta (FR-007): toda cuenta se crea {@code ACTIVA}; el agregado
 * soporta la transición a {@code BLOQUEADA} (método {@code bloquear()}) para
 * sustentar SPEC-004/005.
 */
public enum EstadoCuenta {
    ACTIVA,
    BLOQUEADA
}
