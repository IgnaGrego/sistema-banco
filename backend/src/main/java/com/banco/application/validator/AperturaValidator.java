package com.banco.application.validator;

import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.model.TipoCuenta;

/**
 * Validación de forma de la apertura (ERR-001, errores 400). Clase única con
 * chequeos secuenciales que corta ante el primer error (decisión de
 * docs/architecture/SPEC-002.md §8.2: 2 reglas no justifican la CoR — misma
 * decisión que {@code RegistroValidator}). Lanza
 * {@link DatosInvalidosException(campo, mensaje)} como la CoR de clientes.
 *
 * <p>Chequeos: {@code clienteId} obligatorio (FR-001 — sin él no hay titular);
 * {@code tipo} obligatorio y parseable a {@link TipoCuenta}; {@code moneda}
 * (si viene) debe cumplir {@code ^[A-Z]{3}$}. El formato se valida SIN trim
 * (" ars " es formato inválido → 400). Corta ante el primer error.
 */
public class AperturaValidator {

    public void validar(Long clienteId, String tipo, String moneda) {
        validarClienteId(clienteId);
        validarTipo(tipo);
        validarMoneda(moneda);
    }

    private void validarClienteId(Long clienteId) {
        if (clienteId == null) {
            throw new DatosInvalidosException("clienteId", "El cliente titular es obligatorio");
        }
    }

    private void validarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new DatosInvalidosException("tipo", "El tipo de cuenta es obligatorio");
        }
        try {
            TipoCuenta.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("tipo",
                    "Tipo de cuenta inválido: debe ser CAJA_AHORRO o CUENTA_CORRIENTE");
        }
    }

    private void validarMoneda(String moneda) {
        if (moneda != null && !moneda.isBlank() && !moneda.matches("^[A-Z]{3}$")) {
            throw new DatosInvalidosException("moneda", "Formato de moneda inválido");
        }
    }
}
