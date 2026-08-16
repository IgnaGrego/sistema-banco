package com.banco.domain.model;

import com.banco.domain.vo.DNI;

import java.time.Instant;

/**
 * Agregado raíz del módulo clientes. La construcción no valida más allá del VO
 * {@link DNI}: la cadena de validación de la capa de aplicación es la dueña de
 * obligatoriedad/formato/longitud (BR-001..BR-005).
 */
public class Cliente {

    private final Long id;
    private String nombre;
    private String apellido;
    private DNI dni;
    private String email;
    private String telefono;
    private final Instant fechaAlta;

    /**
     * Constructor público para reconstrucción desde persistencia (adapter).
     */
    public Cliente(Long id, String nombre, String apellido, DNI dni, String email,
                   String telefono, Instant fechaAlta) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.email = email;
        this.telefono = telefono;
        this.fechaAlta = fechaAlta;
    }

    /**
     * Factory de alta: id null (lo asigna la BD), fechaAlta automática (FR-005).
     */
    public static Cliente crear(String nombre, String apellido, DNI dni, String email,
                                String telefono, Instant fechaAlta) {
        return new Cliente(null, nombre, apellido, dni, email, telefono, fechaAlta);
    }

    /**
     * Edición (PUT total, A-004): muta in-place los campos de negocio; id y
     * fechaAlta no son editables.
     */
    public void actualizar(String nombre, String apellido, DNI dni, String email, String telefono) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.email = email;
        this.telefono = telefono;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public DNI getDni() {
        return dni;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }
}
