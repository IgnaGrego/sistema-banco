package com.banco.application.validator;

import java.math.BigDecimal;

/**
 * Entrada del {@link TransferValidator}: normaliza (trim null-safe) el
 * {@code cbuDestino} en un solo lugar.
 */
public record DatosTransferencia(Long cuentaOrigenId, String cbuDestino, BigDecimal monto,
                                 Long clienteIdClaim) {

    public DatosTransferencia {
        cbuDestino = trim(cbuDestino);
    }

    private static String trim(String valor) {
        return valor == null ? null : valor.trim();
    }
}
