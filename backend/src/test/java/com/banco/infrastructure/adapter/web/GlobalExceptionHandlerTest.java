package com.banco.infrastructure.adapter.web;

import com.banco.domain.exception.CbuInvalidoException;
import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.exception.MonedaInvalidaException;
import com.banco.domain.exception.MonedaNoSoportadaException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapeo de las excepciones nuevas de SPEC-002 en el
 * {@link GlobalExceptionHandler} (tabla de docs/architecture/SPEC-002.md
 * §8.5). El handler es una clase plana instanciable sin Spring (constructor
 * sin args; los métodos no usan el contexto), así que se verifica el envelope
 * invocando los métodos directamente y la anotación {@code @ResponseStatus} se
 * lee con reflexión (AC-027 mapeo, ERR-003/005/008/009).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void cuentaNoEncontradaSeMapeaA404CuentaNoEncontrada() throws Exception {
        ErrorResponse response = handler.handleCuentaNoEncontrada(new CuentaNoEncontradaException());

        assertEquals("CUENTA_NO_ENCONTRADA", response.code());
        assertEquals("Cuenta no encontrada", response.message());
        assertNull(response.details());
        assertResponseStatus("handleCuentaNoEncontrada", CuentaNoEncontradaException.class,
                HttpStatus.NOT_FOUND);
    }

    @Test
    void cbuInvalidoSeMapeaA400CbuInvalidoConCampoCbu() throws Exception {
        ErrorResponse response = handler.handleCbuInvalido(new CbuInvalidoException());

        assertEquals("CBU_INVALIDO", response.code());
        assertNotNull(response.details());
        assertEquals("cbu", response.details().get(0).campo());
        assertEquals("El CBU debe contener exactamente 22 dígitos numéricos",
                response.details().get(0).mensaje());
        assertResponseStatus("handleCbuInvalido", CbuInvalidoException.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    void monedaInvalidaSeMapeaA400DatosInvalidosConCampoMoneda() throws Exception {
        ErrorResponse response = handler.handleMonedaInvalida(new MonedaInvalidaException());

        assertEquals("DATOS_INVALIDOS", response.code());
        assertNotNull(response.details());
        assertEquals("moneda", response.details().get(0).campo());
        assertResponseStatus("handleMonedaInvalida", MonedaInvalidaException.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void cuentaBloqueadaSeMapeaA422CuentaBloqueada() throws Exception {
        ErrorResponse response = handler.handleCuentaBloqueada(new CuentaBloqueadaException());

        assertEquals("CUENTA_BLOQUEADA", response.code());
        assertEquals("La cuenta está bloqueada", response.message());
        assertNull(response.details());
        assertResponseStatus("handleCuentaBloqueada", CuentaBloqueadaException.class,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void monedaNoSoportadaSeMapeaA422MonedaNoSoportada() throws Exception {
        ErrorResponse response = handler.handleMonedaNoSoportada(new MonedaNoSoportadaException());

        assertEquals("MONEDA_NO_SOPORTADA", response.code());
        assertEquals("Moneda no soportada", response.message());
        assertNull(response.details());
        assertResponseStatus("handleMonedaNoSoportada", MonedaNoSoportadaException.class,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static void assertResponseStatus(String methodName, Class<?> exceptionType,
                                             HttpStatus expected) throws Exception {
        Method method = GlobalExceptionHandler.class.getMethod(methodName, exceptionType);
        ResponseStatus annotation = method.getAnnotation(ResponseStatus.class);
        assertNotNull(annotation, "Falta @ResponseStatus en " + methodName);
        assertEquals(expected, annotation.value());
    }
}
