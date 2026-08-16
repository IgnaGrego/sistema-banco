package com.banco.application.validator;

import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.model.Rol;

/**
 * Validación de forma/formato del registro (errores 400). Clase única con
 * chequeos secuenciales que corta ante el primer error (desviación deliberada
 * del patrón CoR — docs/architecture/SPEC-003.md §8.2): 4 reglas no justifican
 * 4 clases + orquestador. Lanza {@link DatosInvalidosException(campo, mensaje)}
 * como la CoR de clientes.
 *
 * Chequeos: username obligatorio y ≤ 50 (A-005), password ≥ 8 caracteres
 * (BR-002, ERR-004), rol ∈ {CLIENTE, ADMIN} y, para CLIENTE, clienteId
 * obligatorio (ERR-007).
 */
public class RegistroValidator {

    public void validar(DatosRegistro datos) {
        validarUsername(datos.username());
        validarPassword(datos.password());
        Rol rol = validarRol(datos.rol());
        if (rol == Rol.CLIENTE && datos.clienteId() == null) {
            throw new DatosInvalidosException("clienteId", "Un usuario CLIENTE requiere un clienteId vinculado");
        }
    }

    private void validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new DatosInvalidosException("username", "El username es obligatorio");
        }
        if (username.length() > 50) {
            throw new DatosInvalidosException("username", "El username no puede superar los 50 caracteres");
        }
    }

    private void validarPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new DatosInvalidosException("password", "La password debe tener al menos 8 caracteres");
        }
    }

    private Rol validarRol(String rol) {
        if (rol == null) {
            throw new DatosInvalidosException("rol", "El rol es obligatorio");
        }
        try {
            return Rol.valueOf(rol);
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("rol", "Rol inválido: debe ser CLIENTE o ADMIN");
        }
    }
}
