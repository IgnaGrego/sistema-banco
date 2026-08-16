package com.banco.domain.model;

/**
 * Tipo de cuenta (FR-001). Enum plano; el parsing desde String lo hace
 * {@code AperturaValidator} (capa de aplicación).
 */
public enum TipoCuenta {
    CAJA_AHORRO,
    CUENTA_CORRIENTE
}
