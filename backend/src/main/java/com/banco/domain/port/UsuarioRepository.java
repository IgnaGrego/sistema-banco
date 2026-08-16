package com.banco.domain.port;

import com.banco.domain.model.Usuario;

import java.util.Optional;

/**
 * Puerto de persistencia de usuarios (dominio puro; lo implementa un adaptador
 * en infrastructure).
 */
public interface UsuarioRepository {

    Usuario save(Usuario usuario);

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);
}
