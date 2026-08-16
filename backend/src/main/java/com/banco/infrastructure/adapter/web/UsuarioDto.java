package com.banco.infrastructure.adapter.web;

import com.banco.domain.model.Usuario;

/**
 * Salida del registro (201). Nunca incluye la password ni su hash (BR-001,
 * AC-007): el dominio nunca expone DTOs (ARCHITECTURE.md §7).
 */
public record UsuarioDto(Long id, String username, String rol) {

    public static UsuarioDto from(Usuario usuario) {
        return new UsuarioDto(usuario.getId(), usuario.getUsername(), usuario.getRol().name());
    }
}
