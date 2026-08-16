package com.banco.application.usecase;

import com.banco.application.query.ObtenerClienteQuery;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;

/**
 * Consulta de un cliente (FR-002). Incluye la verificación de propiedad para
 * rol CLIENTE en la capa de aplicación (AF-001, ARCHITECTURE.md §8).
 */
public class ObtenerClienteUseCase {

    private final ClienteRepository repository;

    public ObtenerClienteUseCase(ClienteRepository repository) {
        this.repository = repository;
    }

    public Cliente ejecutar(ObtenerClienteQuery query) {
        if ("CLIENTE".equals(query.rol())
                && (query.clienteIdClaim() == null || !query.clienteIdClaim().equals(query.id()))) {
            throw new AccesoDenegadoException();
        }
        return repository.findById(query.id())
                .orElseThrow(ClienteNoEncontradoException::new);
    }
}
