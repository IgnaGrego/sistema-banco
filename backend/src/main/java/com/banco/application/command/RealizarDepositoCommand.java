package com.banco.application.command;

import java.math.BigDecimal;

/**
 * Entrada del use case de depósito (FR-001): cuenta objetivo, monto en ARS y
 * el sujeto autenticado ({@code rol} y {@code clienteIdClaim}, null para
 * ADMIN — patrón de {@code ObtenerMovimientosQuery}).
 */
public record RealizarDepositoCommand(Long cuentaId, BigDecimal monto, String rol,
                                      Long clienteIdClaim) {
}
