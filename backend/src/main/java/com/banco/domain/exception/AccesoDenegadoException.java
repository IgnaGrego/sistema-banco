package com.banco.domain.exception;

/**
 * Acceso denegado (ERR-003) → 403. Verificación de propiedad de la capa de
 * aplicación (un CLIENTE solo consulta su propio perfil, AF-001).
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException() {
        super("No tiene permisos para realizar esta operación");
    }
}
