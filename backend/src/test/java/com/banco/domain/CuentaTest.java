package com.banco.domain;

import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Agregado Cuenta: constructor de reconstrucción (conserva todos los campos,
 * incl. version) y la transición {@code bloquear()} con su guarda de BR-003
 * (AC-027, dominio): ACTIVA → BLOQUEADA, y operar una cuenta BLOQUEADA
 * (incluido volver a {@code bloquear()}) lanza {@link CuentaBloqueadaException}.
 */
class CuentaTest {

    private static final CBU CBU_VALIDO = new CBU("0000000100010000000011");
    private static final Moneda ARS = new Moneda("ARS");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    private static Cuenta cuentaActiva() {
        return new Cuenta(1L, 7L, CBU_VALIDO, TipoCuenta.CAJA_AHORRO, Money.cero(ARS), ARS,
                EstadoCuenta.ACTIVA, CREATED_AT, 0L);
    }

    @Test
    void constructorDeReconstruccionConservaTodosLosCampos() {
        Money saldo = new Money(new BigDecimal("150.00"), ARS);
        Cuenta cuenta = new Cuenta(42L, 7L, CBU_VALIDO, TipoCuenta.CUENTA_CORRIENTE, saldo, ARS,
                EstadoCuenta.BLOQUEADA, CREATED_AT, 3L);

        assertEquals(42L, cuenta.getId());
        assertEquals(7L, cuenta.getClienteId());
        assertEquals(CBU_VALIDO, cuenta.getCbu());
        assertEquals(TipoCuenta.CUENTA_CORRIENTE, cuenta.getTipo());
        assertEquals(saldo, cuenta.getSaldo());
        assertEquals(ARS, cuenta.getMoneda());
        assertEquals(EstadoCuenta.BLOQUEADA, cuenta.getEstado());
        assertEquals(CREATED_AT, cuenta.getCreatedAt());
        assertEquals(3L, cuenta.getVersion());
    }

    @Test
    void bloquearTransicionaActivaABloqueada() {
        Cuenta cuenta = cuentaActiva();

        cuenta.bloquear();

        assertEquals(EstadoCuenta.BLOQUEADA, cuenta.getEstado());
    }

    @Test
    void bloquearSobreCuentaBloqueadaLanzaCuentaBloqueada() {
        Cuenta cuenta = cuentaActiva();
        cuenta.bloquear();

        // BR-003 / A-001: cualquier operación de negocio (incluido volver a
        // bloquear()) sobre una cuenta BLOQUEADA lanza CuentaBloqueadaException.
        assertThrows(CuentaBloqueadaException.class, cuenta::bloquear);
    }
}
