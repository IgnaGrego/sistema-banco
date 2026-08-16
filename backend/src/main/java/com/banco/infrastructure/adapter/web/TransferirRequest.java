package com.banco.infrastructure.adapter.web;

import java.math.BigDecimal;

/**
 * Entrada de {@code POST /api/v1/transferencias} (FR-001). Record plano sin
 * anotaciones de validación (convención — SPEC-001 §5.1); las reglas viven en
 * el validador y en los VOs.
 */
public record TransferirRequest(Long cuentaOrigenId, String cbuDestino, BigDecimal monto) {
}
