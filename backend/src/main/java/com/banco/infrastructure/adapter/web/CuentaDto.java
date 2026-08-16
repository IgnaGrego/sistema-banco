package com.banco.infrastructure.adapter.web;

import com.banco.domain.model.Cuenta;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Salida REST de una cuenta (el dominio nunca expone DTOs). Los enums se
 * serializan con {@code name()}; el saldo es el monto del VO {@code Money}.
 */
public record CuentaDto(Long id, Long clienteId, String cbu, String tipo, BigDecimal saldo,
                        String moneda, String estado, Instant createdAt) {

    public static CuentaDto from(Cuenta cuenta) {
        return new CuentaDto(cuenta.getId(), cuenta.getClienteId(), cuenta.getCbu().valor(),
                cuenta.getTipo().name(), cuenta.getSaldo().monto(), cuenta.getMoneda().codigo(),
                cuenta.getEstado().name(), cuenta.getCreatedAt());
    }
}
