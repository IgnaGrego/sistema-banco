package com.banco.application.command;

import java.math.BigDecimal;

/**
 * Entrada de la transferencia (FR-001): cuenta origen propia, CBU destino de
 * 22 dígitos y monto en ARS.
 */
public record TransferirCommand(Long cuentaOrigenId, String cbuDestino, BigDecimal monto) {
}
