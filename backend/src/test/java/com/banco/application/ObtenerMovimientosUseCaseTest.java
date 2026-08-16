package com.banco.application;

import com.banco.application.query.ObtenerMovimientosQuery;
import com.banco.application.usecase.ObtenerMovimientosUseCase;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Historial de movimientos (FR-005, AC-016..AC-019 lógica): el CLIENTE solo ve
 * sus propias cuentas (403 si ajena — A-005), el ADMIN cualquier cuenta, y la
 * existencia se verifica ANTES que la propiedad (404-antes-403 —
 * docs/architecture/SPEC-004.md §8.3).
 */
class ObtenerMovimientosUseCaseTest {

    private static final CBU CBU = new CBU("0000003100000000001234");
    private static final Moneda ARS = new Moneda("ARS");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    private CuentaRepository cuentaRepository;
    private MovimientoRepository movimientoRepository;
    private ObtenerMovimientosUseCase useCase;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaRepository.class);
        movimientoRepository = mock(MovimientoRepository.class);
        useCase = new ObtenerMovimientosUseCase(cuentaRepository, movimientoRepository);
    }

    @Test
    void clienteConsultaCuentaPropiaYRecibeLaListaDelRepositorio() {
        stubCuentaExistente(10L, 7L);
        List<Movimiento> esperados = List.of(movimiento(501L, 10L, TipoMovimiento.TRANSFERENCIA_SALIENTE));
        when(movimientoRepository.findByCuentaIdOrderByFechaDesc(10L)).thenReturn(esperados);

        List<Movimiento> resultado = useCase.ejecutar(
                new ObtenerMovimientosQuery(10L, "CLIENTE", 7L));

        assertEquals(esperados, resultado);
        verify(movimientoRepository).findByCuentaIdOrderByFechaDesc(10L);
    }

    @Test
    void clienteConsultaCuentaAjenaYLanzaAccesoDenegado() {
        stubCuentaExistente(10L, 7L);

        assertThrows(AccesoDenegadoException.class, () ->
                useCase.ejecutar(new ObtenerMovimientosQuery(10L, "CLIENTE", 999L)));

        // ERR-006/AC-017: sin consultar el historial.
        verify(movimientoRepository, never()).findByCuentaIdOrderByFechaDesc(any());
    }

    @Test
    void clienteSinClienteIdEnElClaimLanzaAccesoDenegado() {
        stubCuentaExistente(10L, 7L);

        assertThrows(AccesoDenegadoException.class, () ->
                useCase.ejecutar(new ObtenerMovimientosQuery(10L, "CLIENTE", null)));

        verify(movimientoRepository, never()).findByCuentaIdOrderByFechaDesc(any());
    }

    @Test
    void adminConsultaCualquierCuentaYRecibeLaLista() {
        stubCuentaExistente(10L, 7L);
        List<Movimiento> esperados = List.of(movimiento(501L, 10L, TipoMovimiento.TRANSFERENCIA_ENTRANTE));
        when(movimientoRepository.findByCuentaIdOrderByFechaDesc(10L)).thenReturn(esperados);

        List<Movimiento> resultado = useCase.ejecutar(
                new ObtenerMovimientosQuery(10L, "ADMIN", null));

        // A-005: el ADMIN consulta cualquier cuenta (el chequeo de propiedad no
        // aplica; clienteIdClaim null).
        assertEquals(esperados, resultado);
        verify(movimientoRepository).findByCuentaIdOrderByFechaDesc(10L);
    }

    @Test
    void cuentaInexistenteLanzaCuentaNoEncontradaAunqueSeaAdmin() {
        when(cuentaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () ->
                useCase.ejecutar(new ObtenerMovimientosQuery(99L, "ADMIN", null)));

        // ERR-002/AC-019: 404-antes-403 — no se consulta el historial.
        verify(movimientoRepository, never()).findByCuentaIdOrderByFechaDesc(any());
    }

    // --- Helpers ---

    private void stubCuentaExistente(Long cuentaId, Long clienteId) {
        when(cuentaRepository.findById(cuentaId)).thenReturn(
                Optional.of(new Cuenta(cuentaId, clienteId, CBU, TipoCuenta.CAJA_AHORRO,
                        Money.ars(BigDecimal.ZERO), ARS, EstadoCuenta.ACTIVA, CREATED_AT, 0L)));
    }

    private static Movimiento movimiento(Long id, Long cuentaId, TipoMovimiento tipo) {
        return new Movimiento(id, cuentaId, tipo, Money.ars(new BigDecimal("100.00")),
                Instant.parse("2026-08-16T10:30:00Z"), 20L);
    }
}
