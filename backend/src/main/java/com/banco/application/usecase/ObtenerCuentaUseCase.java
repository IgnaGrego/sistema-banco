package com.banco.application.usecase;

import com.banco.application.query.ObtenerCuentaQuery;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.port.CuentaRepository;

/**
 * Consulta de una cuenta por id (FR-004). Propiedad de CLIENTE en la capa de
 * aplicación (BR-006, ARCHITECTURE.md §8).
 *
 * <p>Orden 404 → 403 (docs/architecture/SPEC-002.md §4): a diferencia de
 * {@code ObtenerClienteUseCase} (donde el id ES el clienteId), aquí el id es
 * el de la cuenta y la propiedad solo se conoce tras cargarla; un CLIENTE con
 * claim null que consulta un id inexistente recibe 404, no 403.
 */
public class ObtenerCuentaUseCase {

    private final CuentaRepository repository;

    public ObtenerCuentaUseCase(CuentaRepository repository) {
        this.repository = repository;
    }

    public Cuenta ejecutar(ObtenerCuentaQuery query) {
        // 1. findById → 404 (ERR-003, AC-011).
        Cuenta cuenta = repository.findById(query.id())
                .orElseThrow(CuentaNoEncontradaException::new);

        // 2. Propiedad → 403 (AF-002, AC-010).
        if ("CLIENTE".equals(query.rol())
                && (query.clienteIdClaim() == null
                    || !query.clienteIdClaim().equals(cuenta.getClienteId()))) {
            throw new AccesoDenegadoException();
        }

        // 3. → 200 (AC-008, AC-009).
        return cuenta;
    }
}
