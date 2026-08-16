package com.banco.application;

import com.banco.application.command.RealizarDepositoCommand;
import com.banco.application.usecase.DepositoConfirmacion;
import com.banco.application.usecase.RealizarDepositoUseCase;
import com.banco.application.validator.DatosDepositoRetiro;
import com.banco.application.validator.DepositoRetiroValidado;
import com.banco.application.validator.DepositoRetiroValidator;
import com.banco.domain.event.DepositoRealizado;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.DepositoEventPublisher;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Use case de depósito (AC-001, AC-002, AC-003 lógica): valida, acredita el
 * saldo, persiste la cuenta y exactamente un {@code Movimiento} DEPOSITO (sin
 * contraparte — FR-003), publica UNA vez {@link DepositoRealizado} (FR-004/
 * AC-002) y devuelve la confirmación con {@code idMovimiento} = id del
 * movimiento guardado. Si el validador rechaza, no se persiste nada ni se
 * publica el evento (rollback — FR-001).
 */
class RealizarDepositoUseCaseTest {

    private static final Moneda ARS = new Moneda("ARS");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    private CuentaRepository cuentaRepository;
    private MovimientoRepository movimientoRepository;
    private DepositoRetiroValidator depositoRetiroValidator;
    private DepositoEventPublisher depositoEventPublisher;
    private RealizarDepositoUseCase useCase;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaRepository.class);
        movimientoRepository = mock(MovimientoRepository.class);
        depositoRetiroValidator = mock(DepositoRetiroValidator.class);
        depositoEventPublisher = mock(DepositoEventPublisher.class);
        useCase = new RealizarDepositoUseCase(cuentaRepository, movimientoRepository,
                depositoRetiroValidator, depositoEventPublisher);
    }

    @Test
    void happyPathAcreditaPersisteMovimientoDepositoYPublicaElEvento() {
        Cuenta cuenta = cuenta(10L, 7L, new BigDecimal("100000.00"));
        Money monto = Money.ars(new BigDecimal("15000.00"));
        when(depositoRetiroValidator.validarDeposito(any(DatosDepositoRetiro.class)))
                .thenReturn(new DepositoRetiroValidado(cuenta, monto));
        when(cuentaRepository.save(any(Cuenta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // El save asigna el id (IDENTITY): el unit test stubbea el retorno con
        // id (AC-002, docs/architecture/SPEC-005.md §8.3).
        AtomicLong idMovimiento = new AtomicLong(501L);
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(invocation -> {
            Movimiento m = invocation.getArgument(0);
            return new Movimiento(idMovimiento.get(), m.getCuentaId(), m.getTipo(),
                    m.getMonto(), m.getFecha(), m.getCuentaContraparteId());
        });

        DepositoConfirmacion confirmacion = useCase.ejecutar(
                new RealizarDepositoCommand(10L, new BigDecimal("15000.00"), "ADMIN", null));

        // BR-002: el saldo se acredita exactamente por el monto (AC-001).
        assertEquals(0, cuenta.getSaldo().monto().compareTo(new BigDecimal("115000.00")));

        // FR-003/AC-001: se persiste la cuenta UNA vez y exactamente un
        // movimiento DEPOSITO sin contraparte.
        verify(cuentaRepository, times(1)).save(any(Cuenta.class));
        ArgumentCaptor<Movimiento> movimientoCaptor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(1)).save(movimientoCaptor.capture());
        Movimiento movimiento = movimientoCaptor.getValue();
        assertEquals(TipoMovimiento.DEPOSITO, movimiento.getTipo());
        assertEquals(10L, movimiento.getCuentaId());
        assertEquals(0, movimiento.getMonto().monto().compareTo(monto.monto()));
        assertNull(movimiento.getCuentaContraparteId());

        // FR-004/AC-002: el evento se publica UNA vez con monto, cuentaId,
        // fechaHora (la misma del movimiento) e idMovimiento = id guardado (501).
        ArgumentCaptor<DepositoRealizado> eventoCaptor =
                ArgumentCaptor.forClass(DepositoRealizado.class);
        verify(depositoEventPublisher, times(1)).publicar(eventoCaptor.capture());
        DepositoRealizado evento = eventoCaptor.getValue();
        assertEquals(monto, evento.monto());
        assertEquals(10L, evento.cuentaId());
        assertEquals(movimiento.getFecha(), evento.fechaHora());
        assertEquals(501L, evento.idMovimiento());

        // FR-001/A-002: idMovimiento = id del Movimiento guardado (501).
        assertEquals(501L, confirmacion.idMovimiento());
        assertEquals(10L, confirmacion.cuentaId());
        assertEquals(0, confirmacion.monto().compareTo(new BigDecimal("15000.00")));
        assertEquals(movimiento.getFecha(), confirmacion.fechaHora());
    }

    @Test
    void elValidadorRecibeLosDatosDelCommand() {
        Cuenta cuenta = cuenta(10L, 7L, new BigDecimal("100000.00"));
        when(depositoRetiroValidator.validarDeposito(any(DatosDepositoRetiro.class)))
                .thenReturn(new DepositoRetiroValidado(cuenta, Money.ars(new BigDecimal("15000.00"))));
        when(cuentaRepository.save(any(Cuenta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(new RealizarDepositoCommand(10L, new BigDecimal("15000.00"),
                "ADMIN", null));

        // El use case pasa el command tal cual al validador (passthrough).
        ArgumentCaptor<DatosDepositoRetiro> datosCaptor = ArgumentCaptor.forClass(DatosDepositoRetiro.class);
        verify(depositoRetiroValidator).validarDeposito(datosCaptor.capture());
        DatosDepositoRetiro datos = datosCaptor.getValue();
        assertEquals(10L, datos.cuentaId());
        assertEquals(new BigDecimal("15000.00"), datos.monto());
        assertEquals("ADMIN", datos.rol());
        assertEquals(null, datos.clienteIdClaim());
    }

    @Test
    void validadorRechazaNoSePersisteNadaNiSePublicaElEvento() {
        when(depositoRetiroValidator.validarDeposito(any(DatosDepositoRetiro.class)))
                .thenThrow(new AccesoDenegadoException());

        assertThrows(AccesoDenegadoException.class, () ->
                useCase.ejecutar(new RealizarDepositoCommand(10L, new BigDecimal("15000.00"),
                        "CLIENTE", 7L)));

        // FR-001: rollback total — sin saves de cuentas ni movimientos y sin
        // evento (ERR-005, AC-003 lógica).
        verify(cuentaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
        verify(depositoEventPublisher, never()).publicar(any());
    }

    private static Cuenta cuenta(Long id, Long clienteId, BigDecimal saldo) {
        return new Cuenta(id, clienteId, new CBU("0000003100000000001234"),
                TipoCuenta.CAJA_AHORRO, Money.ars(saldo), ARS, EstadoCuenta.ACTIVA, CREATED_AT, 0L);
    }
}
