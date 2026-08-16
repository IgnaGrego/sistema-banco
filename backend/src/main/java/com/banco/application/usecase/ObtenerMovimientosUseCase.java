package com.banco.application.usecase;

import com.banco.application.query.ObtenerMovimientosQuery;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.Movimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.MovimientoRepository;

import java.util.List;

/**
 * Historial de movimientos de una cuenta (FR-005). Verifica la existencia
 * (404, AC-019) y, para rol CLIENTE, la propiedad de la cuenta (403, AC-017 —
 * A-005). El orden 404-antes-403 es deliberado: la propiedad exige cargar la
 * cuenta (docs/architecture/SPEC-004.md §8.3).
 */
public class ObtenerMovimientosUseCase {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;

    public ObtenerMovimientosUseCase(CuentaRepository cuentaRepository,
                                     MovimientoRepository movimientoRepository) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    public List<Movimiento> ejecutar(ObtenerMovimientosQuery query) {
        Cuenta cuenta = cuentaRepository.findById(query.cuentaId())
                .orElseThrow(CuentaNoEncontradaException::new);

        if ("CLIENTE".equals(query.rol())
                && (query.clienteIdClaim() == null
                || !query.clienteIdClaim().equals(cuenta.getClienteId()))) {
            throw new AccesoDenegadoException();
        }

        // Orden por fecha descendente, sin paginación (A-006).
        return movimientoRepository.findByCuentaIdOrderByFechaDesc(cuenta.getId());
    }
}
