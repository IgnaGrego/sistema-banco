package com.banco.application.validator;

import java.math.BigDecimal;

/**
 * Entrada del {@link DepositoRetiroValidator}: passthrough del command (no hay
 * normalización: campos tipados — docs/architecture/SPEC-005.md §8.3); mantiene
 * el validador desacoplado del command.
 */
public record DatosDepositoRetiro(Long cuentaId, BigDecimal monto, String rol,
                                  Long clienteIdClaim) {
}
