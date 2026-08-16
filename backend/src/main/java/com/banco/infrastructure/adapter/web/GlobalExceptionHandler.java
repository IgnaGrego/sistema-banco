package com.banco.infrastructure.adapter.web;

import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.ClienteDuplicadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.DniInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Traduce excepciones al envelope estándar según la tabla de mapeo de
 * docs/architecture/SPEC-001.md §8.6. Los 401/403 de Spring Security los
 * escriben el entry point y el access-denied handler de SecurityConfig.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DatosInvalidosException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDatosInvalidos(DatosInvalidosException e) {
        return new ErrorResponse("DATOS_INVALIDOS", e.getMessage(),
                List.of(new DetalleError(e.getCampo(), e.getMensaje())));
    }

    @ExceptionHandler(DniInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDniInvalido(DniInvalidoException e) {
        return new ErrorResponse("DATOS_INVALIDOS", e.getMessage(),
                List.of(new DetalleError("dni", e.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return new ErrorResponse("DATOS_INVALIDOS", "JSON malformado o tipo de dato inválido", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return new ErrorResponse("DATOS_INVALIDOS", "Parámetro inválido", null);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccesoDenegado(AccesoDenegadoException e) {
        return new ErrorResponse("ACCESO_DENEGADO", e.getMessage(), null);
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleClienteNoEncontrado(ClienteNoEncontradoException e) {
        return new ErrorResponse("CLIENTE_NO_ENCONTRADO", e.getMessage(), null);
    }

    @ExceptionHandler(ClienteDuplicadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleClienteDuplicado(ClienteDuplicadoException e) {
        return new ErrorResponse("CONFLICTO_UNICIDAD", e.getMessage(),
                List.of(new DetalleError(e.getCampo(), e.getMessage())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // Backstop de carrera: el constraint UNIQUE de la BD no permite
        // identificar el campo; se reporta genérico (§5.4 del diseño).
        log.warn("Conflicto de unicidad capturado por constraint de BD", e);
        return new ErrorResponse("CONFLICTO_UNICIDAD", "Conflicto de unicidad de datos", null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception e) {
        // Se loguea la causa completa para diagnóstico, pero el cliente solo
        // recibe el envelope fijo (no se filtra detalle interno).
        log.error("Error interno no esperado", e);
        return new ErrorResponse("ERROR_INTERNO", "Error interno del servidor", null);
    }
}
