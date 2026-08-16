package com.banco.infrastructure.adapter.web;

/**
 * Entrada de la edición (PUT). Record plano sin anotaciones de validación
 * (las reglas viven en la CoR de application).
 */
public record ActualizarClienteRequest(String nombre, String apellido, String dni,
                                       String email, String telefono) {
}
