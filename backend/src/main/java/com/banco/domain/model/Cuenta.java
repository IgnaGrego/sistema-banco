package com.banco.domain.model;

import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Money;
import com.banco.domain.vo.Moneda;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Agregado raíz del módulo cuentas (A-001 — agregado mínimo requerido por las
 * transferencias, SPEC-004 §10). Invariante de saldo: nunca negativo
 * (BR-001) — {@code debitar} lanza {@link SaldoInsuficienteException} si el
 * monto supera el saldo; {@code acreditar} lo incrementa.
 *
 * {@code version} sostiene el optimistic lock ({@code @Version} en la
 * proyección JPA — BR-006); se mapea en ambos sentidos en el adapter
 * (docs/architecture/SPEC-004.md §8.8).
 */
public class Cuenta {

    private final Long id;
    private final Long clienteId;
    private final CBU cbu;
    private final TipoCuenta tipo;
    private final Moneda moneda;
    private final Instant createdAt;
    private Money saldo;
    private EstadoCuenta estado;
    private Long version;

    /**
     * Constructor público para reconstrucción desde persistencia (adapter).
     */
    public Cuenta(Long id, Long clienteId, CBU cbu, TipoCuenta tipo, Money saldo,
                  Moneda moneda, EstadoCuenta estado, Instant createdAt, Long version) {
        this.id = id;
        this.clienteId = clienteId;
        this.cbu = cbu;
        this.tipo = tipo;
        this.saldo = saldo;
        this.moneda = moneda;
        this.estado = estado;
        this.createdAt = createdAt;
        this.version = version;
    }

    /**
     * Factory de alta: id null (lo asigna la BD), saldo 0, estado ACTIVA y
     * version 0 (A-001, docs/architecture/SPEC-004.md §8.2).
     */
    public static Cuenta crear(Long clienteId, TipoCuenta tipo, CBU cbu, Moneda moneda,
                               Instant createdAt) {
        return new Cuenta(null, clienteId, cbu, tipo,
                new Money(BigDecimal.ZERO, moneda.currency()), moneda,
                EstadoCuenta.ACTIVA, createdAt, 0L);
    }

    /**
     * Debita un monto (BR-001). Doble barrera con el paso 9 del validador:
     * la validación ya verificó el saldo, pero el invariante del agregado se
     * re-verifica aquí (docs/architecture/SPEC-004.md §8.3).
     */
    public void debitar(Money monto) {
        if (monto.esMayorQue(saldo)) {
            throw new SaldoInsuficienteException();
        }
        saldo = saldo.restar(monto);
    }

    /**
     * Acredita un monto (BR-001).
     */
    public void acreditar(Money monto) {
        saldo = saldo.sumar(monto);
    }

    public Long getId() {
        return id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public CBU getCbu() {
        return cbu;
    }

    public TipoCuenta getTipo() {
        return tipo;
    }

    public Money getSaldo() {
        return saldo;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
