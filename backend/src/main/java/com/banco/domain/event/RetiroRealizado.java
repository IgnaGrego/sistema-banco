package com.banco.domain.event;

import com.banco.domain.vo.Money;

import java.time.Instant;

/**
 * Evento de dominio emitido al completar un retiro (FR-004).
 * {@code idMovimiento} es el id del {@code Movimiento} RETIRO generado, que
 * identifica la operación para conciliación.
 */
public record RetiroRealizado(Money monto, Long cuentaId, Instant fechaHora,
                              Long idMovimiento) {
}
