package com.banco.domain.event;

import com.banco.domain.vo.Money;

import java.time.Instant;

/**
 * Evento de dominio emitido al completar un depósito (FR-004).
 * {@code idMovimiento} es el id del {@code Movimiento} DEPOSITO generado, que
 * identifica la operación para conciliación (mismo rol que
 * {@code idMovimientoSaliente} de {@link TransferenciaRealizada}).
 */
public record DepositoRealizado(Money monto, Long cuentaId, Instant fechaHora,
                                Long idMovimiento) {
}
