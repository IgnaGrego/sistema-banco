package com.banco.infrastructure.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Proyección JPA de la tabla {@code movimientos} (sin lógica de negocio). El
 * esquema lo define Flyway (V3__cuentas_y_movimientos.sql); Hibernate solo
 * valida.
 */
@Entity
@Table(name = "movimientos")
public class MovimientoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(nullable = false, length = 22)
    private String tipo;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private Instant fecha;

    @Column(name = "cuenta_contraparte_id")
    private Long cuentaContraparteId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public Instant getFecha() {
        return fecha;
    }

    public void setFecha(Instant fecha) {
        this.fecha = fecha;
    }

    public Long getCuentaContraparteId() {
        return cuentaContraparteId;
    }

    public void setCuentaContraparteId(Long cuentaContraparteId) {
        this.cuentaContraparteId = cuentaContraparteId;
    }
}
