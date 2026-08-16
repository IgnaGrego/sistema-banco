package com.banco.domain.model;

/**
 * Entidad Usuario (SPEC-003): identidad para autenticarse con username +
 * password (hash BCrypt) y operar con un {@link Rol}. {@code clienteId} es el
 * vínculo a un {@link Cliente} existente, solo para usuarios {@code CLIENTE}
 * (FR-005, A-003); es null para {@code ADMIN}.
 *
 * La password se guarda SIEMPRE ya hasheada (BR-001): la entidad nunca recibe
 * la password en claro. La validación de forma/formato (username, password ≥ 8,
 * rol, clienteId condicional) vive en la capa de aplicación
 * ({@code RegistroValidator}), no en la construcción (docs/architecture/SPEC-003.md
 * §8.2).
 */
public class Usuario {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Rol rol;
    private final Long clienteId;

    /**
     * Constructor público para reconstrucción desde persistencia (adapter).
     */
    public Usuario(Long id, String username, String passwordHash, Rol rol, Long clienteId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.clienteId = clienteId;
    }

    /**
     * Factory de alta: id null (lo asigna la BD, como Cliente en SPEC-001 §6.2).
     */
    public static Usuario crear(String username, String passwordHash, Rol rol, Long clienteId) {
        return new Usuario(null, username, passwordHash, rol, clienteId);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public Long getClienteId() {
        return clienteId;
    }
}
