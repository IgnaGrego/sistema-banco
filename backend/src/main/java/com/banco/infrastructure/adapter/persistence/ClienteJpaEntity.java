package com.banco.infrastructure.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Proyección JPA de la tabla {@code clientes} (sin lógica de negocio).
 * El esquema lo define Flyway (V1__schema_inicial.sql); Hibernate solo valida.
 *
 * NOTA fecha_alta: Hibernate 6 mapea {@code java.time.Instant} a
 * "timestamp(6) with time zone" por defecto; la migración V1 usa
 * {@code TIMESTAMP WITH TIME ZONE} para que {@code ddl-auto: validate} pase
 * (desviación documentada de docs/architecture/SPEC-001.md §6.1).
 */
@Entity
@Table(name = "clientes")
public class ClienteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 16)
    private String telefono;

    @Column(name = "fecha_alta", nullable = false)
    private Instant fechaAlta;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(Instant fechaAlta) {
        this.fechaAlta = fechaAlta;
    }
}
