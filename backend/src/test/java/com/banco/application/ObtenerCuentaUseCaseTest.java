package com.banco.application;

import com.banco.application.query.ObtenerCuentaQuery;
import com.banco.application.usecase.ObtenerCuentaUseCase;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consulta de cuenta por id con verificación de propiedad (AC-008, AC-009,
 * AC-010, AC-011). Orden 404 → 403 (SPEC-002 §4): un CLIENTE con claim ajeno
 * que consulta un id inexistente recibe 404, no 403.
 */
class ObtenerCuentaUseCaseTest {

    private CuentaRepository repository;
    private ObtenerCuentaUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(CuentaRepository.class);
        useCase = new ObtenerCuentaUseCase(repository);
    }

    private static Cuenta cuenta() {
        return new Cuenta(1L, 7L, new CBU("0000000100010000000011"), TipoCuenta.CAJA_AHORRO,
                Money.cero(new Moneda("ARS")), new Moneda("ARS"), EstadoCuenta.ACTIVA,
                Instant.parse("2026-08-16T10:00:00Z"), 0L);
    }

    @Test
    void adminConsultaCualquierCuenta() {
        when(repository.findById(1L)).thenReturn(Optional.of(cuenta()));

        Cuenta resultado = useCase.ejecutar(new ObtenerCuentaQuery(1L, "ADMIN", null));

        assertEquals(1L, resultado.getId());
    }

    @Test
    void clienteConsultaSuPropiaCuenta() {
        when(repository.findById(1L)).thenReturn(Optional.of(cuenta()));

        Cuenta resultado = useCase.ejecutar(new ObtenerCuentaQuery(1L, "CLIENTE", 7L));

        assertEquals(1L, resultado.getId());
    }

    @Test
    void clienteConsultaCuentaAjenaLanzaAccesoDenegado() {
        when(repository.findById(1L)).thenReturn(Optional.of(cuenta()));

        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ObtenerCuentaQuery(1L, "CLIENTE", 8L)));
    }

    @Test
    void clienteSinClaimLanzaAccesoDenegado() {
        when(repository.findById(1L)).thenReturn(Optional.of(cuenta()));

        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ObtenerCuentaQuery(1L, "CLIENTE", null)));
    }

    @Test
    void idInexistenteLanzaCuentaNoEncontrada() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> useCase.ejecutar(new ObtenerCuentaQuery(99L, "ADMIN", null)));
    }

    @Test
    void idInexistenteConClienteAjenoLanza404No403() {
        // Orden 404 → 403: la propiedad solo se conoce tras cargar la cuenta,
        // así que un CLIENTE con claim ajeno que consulta un id inexistente
        // recibe CuentaNoEncontradaException (404), no AccesoDenegadoException.
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> useCase.ejecutar(new ObtenerCuentaQuery(99L, "CLIENTE", 8L)));
    }
}
