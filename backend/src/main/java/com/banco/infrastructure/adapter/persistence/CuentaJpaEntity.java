package com.banco.infrastructure.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Proyección JPA de la tabla {@code cuentas} (sin lógica de negocio). El
 * esquema lo define Flyway (V3__cuentas_y_movimientos.sql); Hibernate solo
 * valida.
 *
 * {@code version} con {@link Version} sostiene el optimistic lock (BR-006);
 * se mapea en AMBOS sentidos en {@link CuentaRepositoryAdapter} (crítico para
 * que el lock dispare — docs/architecture/SPEC-004.md §8.8).
 *
 * NOTA {@code createdAt}/{@code fecha}: Hibernate 6 mapea {@code Instant} a
 * "timestamp(6) with time zone"; la migración V3 usa
 * {@code TIMESTAMP WITH TIME ZONE} para que {@code ddl-auto: validate} pase
 * (lección V1).
 */
@Entity
@Table(name = "cuentas")
public class CuentaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 22)
    private String cbu;

    @Column(nullable = false, length = 15)
    private String tipo;

    @Column(nullable = false)
    private BigDecimal saldo;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 9)
    private String estado;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getCbu() {
        return cbu;
    }

    public void setCbu(String cbu) {
        this.cbu = cbu;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
