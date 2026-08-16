package com.banco.domain.model;

/**
 * Tipo de cuenta (A-001, SPEC-004 §10). En el MVP el comportamiento por tipo
 * (comisiones) queda fuera de alcance; el factory de creación recibe el tipo
 * como parámetro (docs/architecture/SPEC-004.md §8.2).
 */
public enum TipoCuenta {
    CAJA_AHORRO,
    CUENTA_CORRIENTE
}
