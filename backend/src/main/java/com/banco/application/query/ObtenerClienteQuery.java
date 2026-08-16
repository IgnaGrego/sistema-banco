package com.banco.application.query;

/**
 * Consulta de un cliente con los datos del sujeto autenticado.
 * {@code rol} ∈ {"ADMIN","CLIENTE"}; {@code clienteIdClaim} es null para ADMIN.
 */
public record ObtenerClienteQuery(Long id, String rol, Long clienteIdClaim) {
}
