package com.banco.domain.model;

import com.banco.domain.vo.Money;

import java.time.Instant;

/**
 * Entidad que registra cada operación sobre una cuenta (FR-003,
 * ARCHITECTURE.md §4). Parte del agregado {@link Cuenta} en la especificación;
 * en la implementación se persiste con un puerto propio
 * (docs/architecture/SPEC-004.md §8.2).
 */
public class Movimiento {

    private final Long id;
    private final Long cuentaId;
    private final TipoMovimiento tipo;
    private final Money monto;
    private final Instant fecha;
    private final Long cuentaContraparteId;

    /**
     * Constructor público para reconstrucción desde persistencia (adapter).
     */
    public Movimiento(Long id, Long cuentaId, TipoMovimiento tipo, Money monto,
                      Instant fecha, Long cuentaContraparteId) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.tipo = tipo;
        this.monto = monto;
        this.fecha = fecha;
        this.cuentaContraparteId = cuentaContraparteId;
    }

    /**
     * Factory de alta: id null (lo asigna la BD).
     */
    public static Movimiento crear(Long cuentaId, TipoMovimiento tipo, Money monto,
                                   Instant fecha, Long cuentaContraparteId) {
        return new Movimiento(null, cuentaId, tipo, monto, fecha, cuentaContraparteId);
    }

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public Money getMonto() {
        return monto;
    }

    public Instant getFecha() {
        return fecha;
    }

    public Long getCuentaContraparteId() {
        return cuentaContraparteId;
    }
}
