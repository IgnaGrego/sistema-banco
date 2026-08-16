package com.banco.domain.exception;

/**
 * Monedas de las cuentas origen y destino incompatibles
 * (ERR-009 → 422 MONEDA_INCOMPATIBLE, BR-007).
 */
public class MonedaIncompatibleException extends RuntimeException {

    public MonedaIncompatibleException() {
        super("Las monedas de las cuentas son incompatibles");
    }
}
