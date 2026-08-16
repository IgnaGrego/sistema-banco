package com.banco.infrastructure.adapter.web;

import com.banco.domain.model.Cliente;

import java.time.Instant;

/**
 * Salida REST de un cliente (el dominio nunca expone DTOs).
 */
public record ClienteDto(Long id, String nombre, String apellido, String dni,
                         String email, String telefono, Instant fechaAlta) {

    public static ClienteDto from(Cliente cliente) {
        return new ClienteDto(cliente.getId(), cliente.getNombre(), cliente.getApellido(),
                cliente.getDni().valor(), cliente.getEmail(), cliente.getTelefono(),
                cliente.getFechaAlta());
    }
}
