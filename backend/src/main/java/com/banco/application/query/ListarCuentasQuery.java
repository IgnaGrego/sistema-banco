package com.banco.application.query;

/**
 * Listado de cuentas con el sujeto autenticado y el filtro opcional.
 * {@code clienteIdFiltro} es el parámetro {@code clienteId} de la request
 * (solo ADMIN lo puede enviar — A-004; un CLIENTE que lo envía recibe 403).
 */
public record ListarCuentasQuery(String rol, Long clienteIdClaim, Long clienteIdFiltro) {
}
