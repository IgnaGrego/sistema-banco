package com.banco.domain.model;

/**
 * Tipo de operación registrada por un {@link Movimiento}
 * (ARCHITECTURE.md §4): depósitos/retiros (SPEC-005) y las dos patas de la
 * transferencia (FR-003).
 */
public enum TipoMovimiento {
    DEPOSITO,
    RETIRO,
    TRANSFERENCIA_ENTRANTE,
    TRANSFERENCIA_SALIENTE
}
