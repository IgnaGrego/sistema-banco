package com.banco.application.validator;

/**
 * Entrada única de la cadena de validación. Normaliza (trim null-safe) nombre,
 * apellido y email en un solo lugar (BR-004: "luego de recortar espacios").
 */
public record DatosCliente(String nombre, String apellido, String dni,
                           String email, String telefono) {

    public DatosCliente {
        nombre = trim(nombre);
        apellido = trim(apellido);
        email = trim(email);
    }

    private static String trim(String valor) {
        return valor == null ? null : valor.trim();
    }
}
