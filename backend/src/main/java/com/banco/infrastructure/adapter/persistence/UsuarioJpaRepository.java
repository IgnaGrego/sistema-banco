package com.banco.infrastructure.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso Spring Data para usuarios (queries derivadas; la unicidad de username
 * la garantiza el constraint UNIQUE de la BD como backstop ante carreras).
 */
public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    Optional<UsuarioJpaEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
