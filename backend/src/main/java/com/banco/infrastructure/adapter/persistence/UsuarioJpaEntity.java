package com.banco.infrastructure.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proyección JPA de la tabla {@code usuarios} (sin lógica de negocio).
 * El esquema lo define Flyway (V2__usuarios.sql); Hibernate solo valida
 * (ddl-auto: validate) — tipos: BIGSERIAL ↔ Long, VARCHAR(n) ↔ length(n),
 * sin columnas Instant (docs/architecture/SPEC-003.md §6.1).
 *
 * {@code clienteId} es un Long plano (nullable): la FK la define la migración;
 * no se mapea relación JPA (sin AttributeConverter, convención de SPEC-001).
 */
@Entity
@Table(name = "usuarios")
public class UsuarioJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 7)
    private String rol;

    @Column(name = "cliente_id")
    private Long clienteId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }
}
