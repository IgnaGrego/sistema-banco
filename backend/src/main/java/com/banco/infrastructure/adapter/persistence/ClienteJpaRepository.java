package com.banco.infrastructure.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso Spring Data para clientes (queries derivadas para las verificaciones
 * de unicidad y el listado ordenado por id ascendente).
 */
public interface ClienteJpaRepository extends JpaRepository<ClienteJpaEntity, Long> {

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<ClienteJpaEntity> findAllByOrderByIdAsc();
}
