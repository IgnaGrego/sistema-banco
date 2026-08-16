package com.banco.application.usecase;

import com.banco.application.query.ListarCuentasQuery;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.CuentaRepository;

import java.util.List;

/**
 * Listado de cuentas (FR-006, A-004). CLIENTE: claim obligatorio y sin
 * parámetro (403 si falta o si envía {@code clienteId}); ADMIN: filtro
 * opcional con validación del cliente (404 si inexistente — ERR-004/AF-004).
 * Java puro, sin Spring.
 */
public class ListarCuentasUseCase {

    private final CuentaRepository repository;
    private final ClienteRepository clienteRepository;

    public ListarCuentasUseCase(CuentaRepository repository, ClienteRepository clienteRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    public List<Cuenta> ejecutar(ListarCuentasQuery query) {
        if ("CLIENTE".equals(query.rol())) {
            // 1a. Claim null → 403 (AF-001, AC-023).
            if (query.clienteIdClaim() == null) {
                throw new AccesoDenegadoException();
            }
            // 1b. El CLIENTE no envía el parámetro → 403 (A-004, AC-019).
            if (query.clienteIdFiltro() != null) {
                throw new AccesoDenegadoException();
            }
            // 1c. Solo sus propias cuentas (FR-006, AC-018).
            return repository.findByClienteId(query.clienteIdClaim());
        }

        // rol == ADMIN
        if (query.clienteIdFiltro() != null) {
            // 2a. El cliente del filtro debe existir → 404 (AF-004, ERR-004, AC-021).
            if (clienteRepository.findById(query.clienteIdFiltro()).isEmpty()) {
                throw new ClienteNoEncontradoException();
            }
            // AC-020.
            return repository.findByClienteId(query.clienteIdFiltro());
        }

        // 2b. Todas las cuentas, ordenadas por id asc (AC-022).
        return repository.findAll();
    }
}
