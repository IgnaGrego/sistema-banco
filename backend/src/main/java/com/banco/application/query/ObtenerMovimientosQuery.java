package com.banco.application.query;

/**
 * Consulta del historial de movimientos de una cuenta (FR-005) con los datos
 * del sujeto autenticado (patrón de {@code ObtenerClienteQuery}).
 * {@code rol} ∈ {"ADMIN","CLIENTE"}; {@code clienteIdClaim} es null para
 * ADMIN.
 */
public record ObtenerMovimientosQuery(Long cuentaId, String rol, Long clienteIdClaim) {
}
