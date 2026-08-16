package com.banco.application.validator;

import com.banco.domain.model.Cuenta;
import com.banco.domain.vo.Money;

/**
 * Resultado de la validación de la transferencia: cuentas origen y destino ya
 * cargadas + monto validado (evita recargarlas en el use case —
 * docs/architecture/SPEC-004.md §8.3).
 */
public record TransferenciaValidada(Cuenta origen, Cuenta destino, Money monto) {
}
