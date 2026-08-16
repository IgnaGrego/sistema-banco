package com.banco.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado del use case de depósito: es a la vez el contrato de salida del
 * use case y el body de la respuesta 201 (FR-001, A-002 —
 * docs/architecture/SPEC-005.md §8.3). {@code idMovimiento} es el id del
 * {@code Movimiento} DEPOSITO generado.
 */
public record DepositoConfirmacion(Long idMovimiento, Long cuentaId, BigDecimal monto,
                                   Instant fechaHora) {
}
