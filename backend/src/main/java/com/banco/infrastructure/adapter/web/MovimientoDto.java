package com.banco.infrastructure.adapter.web;

import com.banco.domain.model.Movimiento;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Salida de {@code GET /api/v1/cuentas/{id}/movimientos} (FR-005; el dominio
 * nunca expone DTOs).
 */
public record MovimientoDto(Long id, Long cuentaId, String tipo, BigDecimal monto,
                            String moneda, Instant fecha, Long cuentaContraparteId) {

    public static MovimientoDto from(Movimiento movimiento) {
        return new MovimientoDto(movimiento.getId(), movimiento.getCuentaId(),
                movimiento.getTipo().name(), movimiento.getMonto().monto(),
                movimiento.getMonto().moneda().codigo(),
                movimiento.getFecha(), movimiento.getCuentaContraparteId());
    }
}
