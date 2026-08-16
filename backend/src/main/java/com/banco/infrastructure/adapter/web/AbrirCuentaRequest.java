package com.banco.infrastructure.adapter.web;

/**
 * Entrada de la apertura (POST /api/v1/cuentas). Record plano sin anotaciones
 * de validación: las reglas de negocio (BR-005, A-005) se validan en
 * {@code AperturaValidator} y en los VOs de dominio.
 */
public record AbrirCuentaRequest(Long clienteId, String tipo, String moneda) {
}
