package com.banco.infrastructure.adapter.persistence;

import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import com.banco.domain.port.UsuarioRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link UsuarioRepository}. Mapeo explícito
 * Usuario ↔ UsuarioJpaEntity (el rol se guarda como String = rol.name() /
 * Rol.valueOf(...); sin AttributeConverter — convención de SPEC-001).
 */
@Component
public class UsuarioRepositoryAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository jpaRepository;

    public UsuarioRepositoryAdapter(UsuarioJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Usuario save(Usuario usuario) {
        return toDomain(jpaRepository.save(toEntity(usuario)));
    }

    @Override
    public Optional<Usuario> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    private UsuarioJpaEntity toEntity(Usuario usuario) {
        UsuarioJpaEntity entity = new UsuarioJpaEntity();
        entity.setId(usuario.getId());
        entity.setUsername(usuario.getUsername());
        entity.setPasswordHash(usuario.getPasswordHash());
        entity.setRol(usuario.getRol().name());
        entity.setClienteId(usuario.getClienteId());
        return entity;
    }

    private Usuario toDomain(UsuarioJpaEntity entity) {
        return new Usuario(entity.getId(), entity.getUsername(), entity.getPasswordHash(),
                Rol.valueOf(entity.getRol()), entity.getClienteId());
    }
}
