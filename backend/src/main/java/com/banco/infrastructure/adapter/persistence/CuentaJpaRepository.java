package com.banco.infrastructure.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso Spring Data para cuentas. Queries derivadas: búsquedas por cbu
 * (FR-005, BR-001) y listados ordenados por id ascendente (FR-006, A-004).
 */
public interface CuentaJpaRepository extends JpaRepository<CuentaJpaEntity, Long> {

    Optional<CuentaJpaEntity> findByCbu(String cbu);

    List<CuentaJpaEntity> findAllByOrderByIdAsc();

    List<CuentaJpaEntity> findByClienteIdOrderByIdAsc(Long clienteId);

    boolean existsByCbu(String cbu);
}
