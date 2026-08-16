package com.banco.domain;

import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Entidad Movimiento (FR-003, AC-021): la factory {@code crear} produce una
 * instancia con id null (lo asigna la BD) y conserva todos los campos.
 */
class MovimientoTest {

    private static final Money MONTO = Money.ars(new BigDecimal("1500.00"));
    private static final Instant FECHA = Instant.parse("2026-08-16T10:30:00Z");

    @Test
    void crearProduceUnMovimientoSinIdConTodosLosCampos() {
        Movimiento movimiento = Movimiento.crear(
                10L, TipoMovimiento.TRANSFERENCIA_SALIENTE, MONTO, FECHA, 20L);

        assertNull(movimiento.getId());
        assertEquals(10L, movimiento.getCuentaId());
        assertEquals(TipoMovimiento.TRANSFERENCIA_SALIENTE, movimiento.getTipo());
        assertEquals(MONTO, movimiento.getMonto());
        assertEquals(FECHA, movimiento.getFecha());
        assertEquals(20L, movimiento.getCuentaContraparteId());
    }

    @Test
    void constructorDeReconstruccionConservaElId() {
        Movimiento movimiento = new Movimiento(
                501L, 10L, TipoMovimiento.TRANSFERENCIA_ENTRANTE, MONTO, FECHA, 10L);

        assertEquals(501L, movimiento.getId());
        assertEquals(TipoMovimiento.TRANSFERENCIA_ENTRANTE, movimiento.getTipo());
        assertEquals(10L, movimiento.getCuentaContraparteId());
    }

    @Test
    void contrapartePuedeSerNullParaOperacionesSinContraparte() {
        Movimiento movimiento = Movimiento.crear(
                10L, TipoMovimiento.DEPOSITO, MONTO, FECHA, null);

        assertNull(movimiento.getCuentaContraparteId());
    }
}
