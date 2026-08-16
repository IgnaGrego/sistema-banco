package com.banco.infrastructure.adapter.web;

import java.math.BigDecimal;

/**
 * Entrada de {@code POST /api/v1/depositos} (FR-001). Record plano sin
 * anotaciones de validación (convención — SPEC-001 §5.1); las reglas viven en
 * {@code DepositoRetiroValidator} y en los VOs.
 */
public record DepositoRequest(Long cuentaId, BigDecimal monto) {
}
