package com.banco.domain.port;

import com.banco.domain.model.Cliente;
import com.banco.domain.vo.DNI;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de clientes (dominio puro; lo implementa un adaptador
 * en infrastructure).
 */
public interface ClienteRepository {

    Cliente save(Cliente cliente);

    Optional<Cliente> findById(Long id);

    /**
     * Devuelve todos los clientes, ordenados por id ascendente (AC-020, A-003).
     */
    List<Cliente> findAll();

    boolean existsByDni(DNI dni);

    boolean existsByDniAndIdNot(DNI dni, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}
