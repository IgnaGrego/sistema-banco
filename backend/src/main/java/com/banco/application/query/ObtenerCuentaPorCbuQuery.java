package com.banco.application.query;

/**
 * Consulta de una cuenta por cbu con los datos del sujeto autenticado.
 */
public record ObtenerCuentaPorCbuQuery(String cbu, String rol, Long clienteIdClaim) {
}
