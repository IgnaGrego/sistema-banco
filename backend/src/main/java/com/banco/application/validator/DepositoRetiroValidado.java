package com.banco.application.validator;

import com.banco.domain.model.Cuenta;
import com.banco.domain.vo.Money;

/**
 * Resultado de la validación del depósito/retiro: cuenta ya cargada + monto
 * validado (evita recargarla en el use case —
 * docs/architecture/SPEC-005.md §8.3).
 */
public record DepositoRetiroValidado(Cuenta cuenta, Money monto) {
}
