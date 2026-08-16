package com.banco.domain.port;

import com.banco.domain.model.Movimiento;

import java.util.List;

/**
 * Puerto de persistencia de movimientos (dominio puro). Puerto separado del
 * agregado {@code Cuenta} porque el historial es un read model (CQRS ligero —
 * docs/architecture/SPEC-004.md §8.2).
 */
public interface MovimientoRepository {

    Movimiento save(Movimiento movimiento);

    /**
     * Historial de una cuenta, ordenado por fecha descendente (FR-005, A-006).
     */
    List<Movimiento> findByCuentaIdOrderByFechaDesc(Long cuentaId);
}
