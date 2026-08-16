package com.banco.infrastructure.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso Spring Data para cuentas.
 */
public interface CuentaJpaRepository extends JpaRepository<CuentaJpaEntity, Long> {

    Optional<CuentaJpaEntity> findByCbu(String cbu);

    List<CuentaJpaEntity> findByClienteId(Long clienteId);
}
