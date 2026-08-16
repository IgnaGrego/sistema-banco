package com.banco.infrastructure.adapter.web;

/**
 * Entrada del alta (POST). Record plano sin anotaciones de validación: las
 * reglas de negocio (BR-001..BR-005) se validan en la CoR de application.
 */
public record CrearClienteRequest(String nombre, String apellido, String dni,
                                  String email, String telefono) {
}
