package com.banco.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado del use case de transferencia: es a la vez el contrato de salida
 * del use case y el body de la respuesta 201 (FR-001, A-007 —
 * docs/architecture/SPEC-004.md §8.3). {@code idTransferencia} es el id del
 * {@code Movimiento} TRANSFERENCIA_SALIENTE generado.
 */
public record TransferenciaConfirmacion(Long idTransferencia, BigDecimal monto,
                                        String cbuDestino, Instant fechaHora) {
}
