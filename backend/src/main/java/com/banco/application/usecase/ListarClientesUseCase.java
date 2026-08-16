package com.banco.application.usecase;

import com.banco.application.query.ListarClientesQuery;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;

import java.util.List;

/**
 * Listado de clientes (FR-004): delega en el puerto; el orden por id
 * ascendente lo garantiza el adapter (AC-020, A-003).
 */
public class ListarClientesUseCase {

    private final ClienteRepository repository;

    public ListarClientesUseCase(ClienteRepository repository) {
        this.repository = repository;
    }

    public List<Cliente> ejecutar(ListarClientesQuery query) {
        return repository.findAll();
    }
}
