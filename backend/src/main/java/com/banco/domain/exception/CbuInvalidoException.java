package com.banco.domain.exception;

/**
 * CBU inválido (ERR-005): no cumple {@code ^[0-9]{22}$}. La lanza el VO
 * {@code CBU} (BR-001), también en la consulta por {@code cbu}.
 */
public class CbuInvalidoException extends RuntimeException {

    public CbuInvalidoException() {
        super("El CBU debe contener exactamente 22 dígitos numéricos");
    }
}
