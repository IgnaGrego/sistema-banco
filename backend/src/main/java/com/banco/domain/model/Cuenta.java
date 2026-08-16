package com.banco.domain.model;

import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;

import java.time.Instant;

/**
 * Agregado raíz del módulo cuentas (ARCHITECTURE.md §4). Único punto de
 * mutación del saldo; valida a través de sus VOs y enums (CBU, Money, Moneda
 * validan en construcción). Incluye la transición {@code bloquear()} (FR-007,
 * A-003) y la guarda de BR-003 (A-001).
 *
 * <p>Invariante: {@code saldo.moneda() == moneda} (garantizado por la
 * {@code CuentaFactory}; el adapter mapea ambas a columnas separadas).
 *
 * <p>El constructor público queda solo para reconstrucción desde persistencia
 * (convención de {@code Cliente}); la creación la centraliza
 * {@code CuentaFactory} (BR-004).
 */
public class Cuenta {

    private final Long id;
    private final Long clienteId;
    private final CBU cbu;
    private final TipoCuenta tipo;
    private Money saldo;
    private final Moneda moneda;
    private EstadoCuenta estado;
    private final Instant createdAt;
    private final Long version;

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
     * Transición {@code ACTIVA → BLOQUEADA} (FR-007, A-003). La guarda de
     * BR-003 (A-001) lanza {@link CuentaBloqueadaException} ante cualquier
     * operación de negocio sobre una cuenta {@code BLOQUEADA} (incluido volver
     * a {@code bloquear()}); los métodos de dinero de SPEC-004/005 invocarán
     * esta misma guarda primero.
     */
    public void bloquear() {
        verificarActiva();
        this.estado = EstadoCuenta.BLOQUEADA;
    }

    private void verificarActiva() {
        if (estado != EstadoCuenta.ACTIVA) {
            throw new CuentaBloqueadaException();
        }
    }

    /**
     * Debita un monto de la cuenta (BR-001 de SPEC-004): invariante saldo
     * nunca negativo. Invoca primero la guarda {@code verificarActiva()}
     * (BR-002/ERR-003); si {@code monto > saldo} lanza
     * {@link SaldoInsuficienteException} (ERR-001). Doble barrera con el paso 9
     * del {@code TransferValidator}.
     */
    public void debitar(Money monto) {
        verificarActiva();
        if (monto.esMayorQue(saldo)) {
            throw new SaldoInsuficienteException();
        }
        saldo = saldo.restar(monto);
    }

    /**
     * Acredita un monto a la cuenta (BR-001 de SPEC-004). Invoca primero la
     * guarda {@code verificarActiva()} (BR-002/ERR-003).
     */
    public void acreditar(Money monto) {
        verificarActiva();
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
