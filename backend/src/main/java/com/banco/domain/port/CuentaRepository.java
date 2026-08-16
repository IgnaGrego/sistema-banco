package com.banco.domain.port;

import com.banco.domain.model.Cuenta;
import com.banco.domain.vo.CBU;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de cuentas (dominio puro; lo implementa un adaptador
 * en infrastructure). Sin {@code existsByClienteId}: no tiene consumidor en
 * este sprint (AF-004/ERR-004 se resuelve con {@code ClienteRepository.findById}
 * — ver docs/architecture/SPEC-002.md §13).
 */
public interface CuentaRepository {

    Cuenta save(Cuenta cuenta);

    Optional<Cuenta> findById(Long id);

    Optional<Cuenta> findByCbu(CBU cbu);

    /**
     * Devuelve las cuentas del cliente, ordenadas por id ascendente (FR-006).
     */
    List<Cuenta> findByClienteId(Long clienteId);

    /**
     * Devuelve todas las cuentas, ordenadas por id ascendente (FR-006, A-004).
     */
    List<Cuenta> findAll();

    boolean existsByCbu(CBU cbu);
}
