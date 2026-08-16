package com.banco.domain.exception;

/**
 * Límite diario de transferencias del cliente alcanzado o superado
 * (ERR-007 → 422 LIMITE_DIARIO_EXCEDIDO, BR-004/AF-002).
 */
public class LimiteDiarioExcedidoException extends RuntimeException {

    public LimiteDiarioExcedidoException() {
        super("Límite diario de transferencias excedido");
    }
}
