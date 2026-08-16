package com.banco.application.command;

import java.math.BigDecimal;

/**
 * Entrada del use case de retiro (FR-002): cuenta objetivo, monto en ARS y el
 * sujeto autenticado ({@code rol} y {@code clienteIdClaim} — patrón de
 * {@code ObtenerMovimientosQuery}).
 */
public record RealizarRetiroCommand(Long cuentaId, BigDecimal monto, String rol,
                                    Long clienteIdClaim) {
}
