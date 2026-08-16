package com.banco.domain.exception;

/**
 * CBU inválido (defensiva del VO {@code CBU}): no cumple {@code ^[0-9]{22}$}.
 * Mapeada a 400 con campo {@code cbuDestino} en el handler (misma semántica
 * que {@link DniInvalidoException}); el validador de la transferencia la
 * traduce a {@link DatosInvalidosException} con campo "cbuDestino"
 * (docs/architecture/SPEC-004.md §8.4).
 */
public class CbuInvalidoException extends RuntimeException {

    public CbuInvalidoException() {
        super("El CBU debe contener exactamente 22 dígitos");
    }
}
