package com.banco.application.usecase;

import com.banco.application.query.ObtenerCuentaPorCbuQuery;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;

/**
 * Consulta de una cuenta por cbu (FR-005). El VO {@link CBU} valida el formato
 * (400, ERR-005); la propiedad de CLIENTE se verifica en la capa de aplicación
 * (BR-006). Orden: CBU (400) → findByCbu (404) → propiedad (403).
 */
public class ObtenerCuentaPorCbuUseCase {

    private final CuentaRepository repository;

    public ObtenerCuentaPorCbuUseCase(CuentaRepository repository) {
        this.repository = repository;
    }

    public Cuenta ejecutar(ObtenerCuentaPorCbuQuery query) {
        // 1. Formato del CBU → 400 CBU_INVALIDO (ERR-005, AC-017).
        CBU cbu = new CBU(query.cbu());

        // 2. findByCbu → 404 (ERR-003, AC-016).
        Cuenta cuenta = repository.findByCbu(cbu)
                .orElseThrow(CuentaNoEncontradaException::new);

        // 3. Propiedad → 403 (AF-002, AC-015).
        if ("CLIENTE".equals(query.rol())
                && (query.clienteIdClaim() == null
                    || !query.clienteIdClaim().equals(cuenta.getClienteId()))) {
            throw new AccesoDenegadoException();
        }

        // 4. → 200 (AC-013, AC-014).
        return cuenta;
    }
}
