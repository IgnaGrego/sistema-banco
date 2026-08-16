package com.banco.domain.exception;

/**
 * Auto-transferencia: el CBU destino es el CBU de la propia cuenta origen
 * (ERR-008 → 422 AUTO_TRANSFERENCIA, BR-005).
 */
public class AutoTransferenciaException extends RuntimeException {

    public AutoTransferenciaException() {
        super("No se puede transferir a la misma cuenta");
    }
}
