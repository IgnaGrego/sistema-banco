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
 * esquema lo define Flyway (V3__cuentas.sql); Hibernate solo valida
 * ({@code ddl-auto: validate}).
 *
 * <p>{@code @Version} (optimistic locking, ARCHITECTURE.md §5): {@code Cuenta}
 * es el agregado que moverá dinero en SPEC-004/005; el campo {@code version}
 * se persiste y se reconstruye por el adapter en ambas direcciones.
 *
 * <p>NOTA created_at: Hibernate 6 mapea {@code java.time.Instant} a
 * "timestamp(6) with time zone"; la migración V3 usa
 * {@code TIMESTAMP WITH TIME ZONE} (desviación documentada, misma mecánica que
 * {@code fecha_alta} de V1).
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

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 20)
    private String estado;

    @Version
    @Column(nullable = false) // version BIGINT NOT NULL DEFAULT 0 (V3)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
