package com.banco.application;

import com.banco.application.query.ObtenerCuentaPorCbuQuery;
import com.banco.application.usecase.ObtenerCuentaPorCbuUseCase;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CbuInvalidoException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consulta de cuenta por cbu (AC-013..AC-017): formato (400), inexistente
 * (404) y propiedad de CLIENTE (403).
 */
class ObtenerCuentaPorCbuUseCaseTest {

    private static final String CBU_STR = "0000000100010000000011";

    private CuentaRepository repository;
    private ObtenerCuentaPorCbuUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(CuentaRepository.class);
        useCase = new ObtenerCuentaPorCbuUseCase(repository);
    }

    private static Cuenta cuenta() {
        return new Cuenta(1L, 7L, new CBU(CBU_STR), TipoCuenta.CAJA_AHORRO,
                Money.cero(new Moneda("ARS")), new Moneda("ARS"), EstadoCuenta.ACTIVA,
                Instant.parse("2026-08-16T10:00:00Z"), 0L);
    }

    @Test
    void adminConsultaPorCbuExistente() {
        when(repository.findByCbu(any(CBU.class))).thenReturn(Optional.of(cuenta()));

        Cuenta resultado = useCase.ejecutar(new ObtenerCuentaPorCbuQuery(CBU_STR, "ADMIN", null));

        assertEquals(CBU_STR, resultado.getCbu().valor());
    }

    @Test
    void clienteConsultaCbuPropio() {
        when(repository.findByCbu(any(CBU.class))).thenReturn(Optional.of(cuenta()));

        Cuenta resultado = useCase.ejecutar(new ObtenerCuentaPorCbuQuery(CBU_STR, "CLIENTE", 7L));

        assertEquals(1L, resultado.getId());
    }

    @Test
    void clienteConsultaCbuAjenoLanzaAccesoDenegado() {
        when(repository.findByCbu(any(CBU.class))).thenReturn(Optional.of(cuenta()));

        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ObtenerCuentaPorCbuQuery(CBU_STR, "CLIENTE", 8L)));
    }

    @Test
    void cbuInexistenteLanzaCuentaNoEncontrada() {
        when(repository.findByCbu(any(CBU.class))).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> useCase.ejecutar(new ObtenerCuentaPorCbuQuery(CBU_STR, "ADMIN", null)));
    }

    @Test
    void cbuMalformadoLanzaCbuInvalidoSinTocarElRepositorio() {
        // 21 dígitos: el VO CBU lo rechaza antes de consultar (ERR-005, AC-017).
        assertThrows(CbuInvalidoException.class,
                () -> useCase.ejecutar(new ObtenerCuentaPorCbuQuery("123456789012345678901", "ADMIN", null)));
        verify(repository, never()).findByCbu(any(CBU.class));
    }
}
