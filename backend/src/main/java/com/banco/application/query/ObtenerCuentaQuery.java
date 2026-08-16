package com.banco.application.query;

/**
 * Consulta de una cuenta por id con los datos del sujeto autenticado (misma
 * forma que {@code ObtenerClienteQuery}). {@code rol} ∈ {"ADMIN","CLIENTE"};
 * {@code clienteIdClaim} es null para ADMIN.
 */
public record ObtenerCuentaQuery(Long id, String rol, Long clienteIdClaim) {
}
