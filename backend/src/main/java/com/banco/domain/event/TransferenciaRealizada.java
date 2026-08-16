package com.banco.domain.event;

import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Money;

import java.time.Instant;

/**
 * Evento de dominio emitido al completar una transferencia (FR-004).
 * {@code idMovimientoSaliente} es el id del {@code Movimiento}
 * TRANSFERENCIA_SALIENTE generado, que identifica la operación para
 * conciliación (A-007).
 */
public record TransferenciaRealizada(Money monto, CBU cbuOrigen, CBU cbuDestino,
                                     Instant fechaHora, Long idMovimientoSaliente) {
}
