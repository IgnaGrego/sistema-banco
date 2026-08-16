package com.banco.infrastructure.adapter.web;

import com.banco.domain.exception.AutoTransferenciaException;
import com.banco.domain.exception.CbuInvalidoException;
import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.exception.LimiteDiarioExcedidoException;
import com.banco.domain.exception.MonedaIncompatibleException;
import com.banco.domain.exception.MonedaInvalidaException;
import com.banco.domain.exception.MonedaNoSoportadaException;
import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.model.Cuenta;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapeo de las excepciones de SPEC-002/004 en el {@link GlobalExceptionHandler}
 * (tablas de docs/architecture/SPEC-002.md §8.5 y SPEC-004.md §8.6). El handler
 * es una clase plana instanciable sin Spring (constructor sin args; los métodos
 * no usan el contexto), así que se verifica el envelope invocando los métodos
 * directamente y la anotación {@code @ResponseStatus} se lee con reflexión.
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

    // --- SPEC-004 (docs/architecture/SPEC-004.md §8.6): ERR-001/005/007/008/009 ---

    @Test
    void saldoInsuficienteSeMapeaA422SaldoInsuficiente() throws Exception {
        ErrorResponse response = handler.handleSaldoInsuficiente(new SaldoInsuficienteException());

        assertEquals("SALDO_INSUFICIENTE", response.code());
        assertEquals("Saldo insuficiente", response.message());
        assertNull(response.details());
        assertResponseStatus("handleSaldoInsuficiente", SaldoInsuficienteException.class,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void limiteDiarioExcedidoSeMapeaA422LimiteDiarioExcedido() throws Exception {
        ErrorResponse response = handler.handleLimiteDiarioExcedido(
                new LimiteDiarioExcedidoException());

        assertEquals("LIMITE_DIARIO_EXCEDIDO", response.code());
        assertEquals("Límite diario de transferencias excedido", response.message());
        assertNull(response.details());
        assertResponseStatus("handleLimiteDiarioExcedido", LimiteDiarioExcedidoException.class,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void autoTransferenciaSeMapeaA422AutoTransferencia() throws Exception {
        ErrorResponse response = handler.handleAutoTransferencia(new AutoTransferenciaException());

        assertEquals("AUTO_TRANSFERENCIA", response.code());
        assertEquals("No se puede transferir a la misma cuenta", response.message());
        assertNull(response.details());
        assertResponseStatus("handleAutoTransferencia", AutoTransferenciaException.class,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void monedaIncompatibleSeMapeaA422MonedaIncompatible() throws Exception {
        ErrorResponse response = handler.handleMonedaIncompatible(new MonedaIncompatibleException());

        assertEquals("MONEDA_INCOMPATIBLE", response.code());
        assertEquals("Las monedas de las cuentas son incompatibles", response.message());
        assertNull(response.details());
        assertResponseStatus("handleMonedaIncompatible", MonedaIncompatibleException.class,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void optimisticLockSeMapeaA409ConflictoConcurrencia() throws Exception {
        ErrorResponse response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException(Cuenta.class, 1L, null));

        // ERR-005/BR-006/A-004: 409 CONFLICTO_CONCURRENCIA, sin reintento.
        assertEquals("CONFLICTO_CONCURRENCIA", response.code());
        assertEquals("Conflicto de concurrencia: reintente la operación", response.message());
        assertNull(response.details());
        assertResponseStatus("handleOptimisticLockingFailure",
                ObjectOptimisticLockingFailureException.class, HttpStatus.CONFLICT);
    }

    private static void assertResponseStatus(String methodName, Class<?> exceptionType,
                                             HttpStatus expected) throws Exception {
        Method method = GlobalExceptionHandler.class.getMethod(methodName, exceptionType);
        ResponseStatus annotation = method.getAnnotation(ResponseStatus.class);
        assertNotNull(annotation, "Falta @ResponseStatus en " + methodName);
        assertEquals(expected, annotation.value());
    }
}
