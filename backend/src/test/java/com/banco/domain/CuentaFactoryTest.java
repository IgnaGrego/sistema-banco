package com.banco.domain;

import com.banco.domain.factory.CuentaFactory;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Factory de creación de cuentas por tipo (BR-004, AC-024): ambos tipos se
 * crean con id null, saldo 0, estado ACTIVA, moneda/cbu/createdAt dados y
 * version 0L (decisión de implementación documentada: version inicial 0L en
 * vez de null, consistente con el DEFAULT 0 de la migración V3).
 */
class CuentaFactoryTest {

    private static final CBU CBU_VALIDO = new CBU("0000000100010000000011");
    private static final Moneda ARS = new Moneda("ARS");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    @Test
    void creaCajaDeAhorroConSaldoCeroYEstadoActiva() {
        Cuenta cuenta = CuentaFactory.crear(7L, TipoCuenta.CAJA_AHORRO, CBU_VALIDO, ARS, CREATED_AT);

        assertNull(cuenta.getId());
        assertEquals(7L, cuenta.getClienteId());
        assertEquals(CBU_VALIDO, cuenta.getCbu());
        assertEquals(TipoCuenta.CAJA_AHORRO, cuenta.getTipo());
        assertEquals(0, cuenta.getSaldo().monto().signum());
        assertEquals(ARS, cuenta.getMoneda());
        assertEquals(EstadoCuenta.ACTIVA, cuenta.getEstado());
        assertEquals(CREATED_AT, cuenta.getCreatedAt());
        assertEquals(0L, cuenta.getVersion());
    }

    @Test
    void creaCuentaCorrienteConSaldoCeroYEstadoActiva() {
        Cuenta cuenta = CuentaFactory.crear(7L, TipoCuenta.CUENTA_CORRIENTE, CBU_VALIDO, ARS, CREATED_AT);

        assertNull(cuenta.getId());
        assertEquals(7L, cuenta.getClienteId());
        assertEquals(CBU_VALIDO, cuenta.getCbu());
        assertEquals(TipoCuenta.CUENTA_CORRIENTE, cuenta.getTipo());
        assertEquals(0, cuenta.getSaldo().monto().signum());
        assertEquals(ARS, cuenta.getMoneda());
        assertEquals(EstadoCuenta.ACTIVA, cuenta.getEstado());
        assertEquals(CREATED_AT, cuenta.getCreatedAt());
        assertEquals(0L, cuenta.getVersion());
    }

    @Test
    void tipoNullLanzaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> CuentaFactory.crear(7L, null, CBU_VALIDO, ARS, CREATED_AT));
    }
}
