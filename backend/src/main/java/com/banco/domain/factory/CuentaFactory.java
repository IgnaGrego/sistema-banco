package com.banco.domain.factory;

import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;

import java.time.Instant;

/**
 * Factory de creación de {@link Cuenta} por tipo (BR-004, ARCHITECTURE.md §5).
 * Hoy ambas ramas producen la misma estructura (saldo 0, estado ACTIVA,
 * version 0); el punto de dispatch por {@code switch} queda explícito para la
 * divergencia futura (comisiones/descubierto de cuenta corriente, SPEC-005 —
 * Strategy).
 *
 * <p>Decisión de implementación: se inicializa {@code version = 0L} (no null)
 * para que el INSERT de Hibernate sea determinista y consistente con el
 * {@code DEFAULT 0} de la migración V3 (ver reporte).
 */
public final class CuentaFactory {

    private CuentaFactory() {
        // Clase utilitaria: solo métodos estáticos.
    }

    public static Cuenta crear(Long clienteId, TipoCuenta tipo, CBU cbu,
                               Moneda moneda, Instant createdAt) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de cuenta es obligatorio");
        }
        return switch (tipo) {
            case CAJA_AHORRO -> construir(clienteId, tipo, cbu, moneda, createdAt);
            case CUENTA_CORRIENTE -> construir(clienteId, tipo, cbu, moneda, createdAt);
            default -> throw new IllegalArgumentException("Tipo de cuenta no soportado: " + tipo);
        };
    }

    private static Cuenta construir(Long clienteId, TipoCuenta tipo, CBU cbu,
                                    Moneda moneda, Instant createdAt) {
        return new Cuenta(null, clienteId, cbu, tipo, Money.cero(moneda), moneda,
                EstadoCuenta.ACTIVA, createdAt, 0L);
    }
}
