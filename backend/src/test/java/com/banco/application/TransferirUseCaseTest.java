package com.banco.application;

import com.banco.application.command.TransferirCommand;
import com.banco.application.usecase.TransferenciaConfirmacion;
import com.banco.application.usecase.TransferirUseCase;
import com.banco.application.validator.DatosTransferencia;
import com.banco.application.validator.TransferValidator;
import com.banco.application.validator.TransferenciaValidada;
import com.banco.domain.event.TransferenciaRealizada;
import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.port.TransferenciaEventPublisher;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Use case de transferencia (AC-001, AC-002, AC-003, AC-004 lógica): debita el
 * origen, acredita el destino, persiste las dos cuentas y los dos movimientos
 * (misma fecha y monto, contrapartes cruzadas — FR-003), publica UNA vez
 * {@link TransferenciaRealizada} (FR-004/AC-003) y devuelve la confirmación con
 * {@code idTransferencia} = id del movimiento saliente (A-007). Si el
 * validador rechaza, no se persiste nada ni se publica el evento (FR-002).
 */
class TransferirUseCaseTest {

    private static final CBU CBU_ORIGEN = new CBU("0000003100000000001234");
    private static final CBU CBU_DESTINO = new CBU("0000003100000000005678");
    private static final Moneda ARS = new Moneda("ARS");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    private CuentaRepository cuentaRepository;
    private MovimientoRepository movimientoRepository;
    private TransferValidator transferValidator;
    private TransferenciaEventPublisher eventPublisher;
    private TransferirUseCase useCase;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaRepository.class);
        movimientoRepository = mock(MovimientoRepository.class);
        transferValidator = mock(TransferValidator.class);
        eventPublisher = mock(TransferenciaEventPublisher.class);
        useCase = new TransferirUseCase(cuentaRepository, movimientoRepository,
                transferValidator, eventPublisher);
    }

    @Test
    void happyPathDebitaAcreditaPersisteCuatroVecesYPublicaElEvento() {
        Cuenta origen = cuenta(10L, 7L, CBU_ORIGEN, new BigDecimal("100000.00"));
        Cuenta destino = cuenta(20L, 8L, CBU_DESTINO, BigDecimal.ZERO);
        Money monto = Money.ars(new BigDecimal("15000.00"));
        when(transferValidator.validar(any(DatosTransferencia.class)))
                .thenReturn(new TransferenciaValidada(origen, destino, monto));
        when(cuentaRepository.save(any(Cuenta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // El save asigna el id (IDENTITY): el unit test stubbea el retorno con
        // id (AC-003, docs/architecture/SPEC-004.md §12). Se registran los
        // objetos DEVUELTOS (persistidos), no los argumentos (id null).
        AtomicLong idMovimiento = new AtomicLong(501L);
        List<Movimiento> guardados = new ArrayList<>();
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(invocation -> {
            Movimiento m = invocation.getArgument(0);
            Movimiento guardado = new Movimiento(idMovimiento.getAndIncrement(), m.getCuentaId(),
                    m.getTipo(), m.getMonto(), m.getFecha(), m.getCuentaContraparteId());
            guardados.add(guardado);
            return guardado;
        });

        TransferenciaConfirmacion confirmacion = useCase.ejecutar(
                new TransferirCommand(10L, CBU_DESTINO.valor(), new BigDecimal("15000.00")), 7L);

        // BR-001: el saldo del origen se debita y el del destino se acredita
        // exactamente por el monto (AC-001).
        assertEquals(0, origen.getSaldo().monto().compareTo(new BigDecimal("85000.00")));
        assertEquals(0, destino.getSaldo().monto().compareTo(new BigDecimal("15000.00")));

        // FR-002: se persisten las dos cuentas (AC-001) y los dos movimientos.
        verify(cuentaRepository, times(2)).save(any(Cuenta.class));
        verify(movimientoRepository, times(2)).save(any(Movimiento.class));

        // FR-003/AC-002: exactamente dos movimientos con el MISMO monto y la
        // MISMA fecha y contrapartes cruzadas (saliente en origen apuntando al
        // destino; entrante en destino apuntando al origen).
        Movimiento saliente = guardados.stream()
                .filter(m -> m.getTipo() == TipoMovimiento.TRANSFERENCIA_SALIENTE).findFirst().orElseThrow();
        Movimiento entrante = guardados.stream()
                .filter(m -> m.getTipo() == TipoMovimiento.TRANSFERENCIA_ENTRANTE).findFirst().orElseThrow();
        assertEquals(10L, saliente.getCuentaId());
        assertEquals(20L, saliente.getCuentaContraparteId());
        assertEquals(20L, entrante.getCuentaId());
        assertEquals(10L, entrante.getCuentaContraparteId());
        assertEquals(0, saliente.getMonto().monto().compareTo(monto.monto()));
        assertEquals(0, entrante.getMonto().monto().compareTo(monto.monto()));
        assertEquals(saliente.getFecha(), entrante.getFecha());

        // FR-004/AC-003: el evento se publica UNA vez con monto, cbuOrigen,
        // cbuDestino, fechaHora e idMovimientoSaliente = id del saliente (501).
        ArgumentCaptor<TransferenciaRealizada> eventoCaptor =
                ArgumentCaptor.forClass(TransferenciaRealizada.class);
        verify(eventPublisher, times(1)).publicar(eventoCaptor.capture());
        TransferenciaRealizada evento = eventoCaptor.getValue();
        assertEquals(monto, evento.monto());
        assertEquals(CBU_ORIGEN, evento.cbuOrigen());
        assertEquals(CBU_DESTINO, evento.cbuDestino());
        assertEquals(saliente.getFecha(), evento.fechaHora());
        assertEquals(saliente.getId(), evento.idMovimientoSaliente());

        // FR-001/A-007: idTransferencia = id del Movimiento saliente (501).
        assertEquals(501L, confirmacion.idTransferencia());
        assertEquals(0, confirmacion.monto().compareTo(new BigDecimal("15000.00")));
        assertEquals(CBU_DESTINO.valor(), confirmacion.cbuDestino());
        assertEquals(saliente.getFecha(), confirmacion.fechaHora());
    }

    @Test
    void elValidadorRecibeElCbuDestinoNormalizadoYElClienteAutenticado() {
        Cuenta origen = cuenta(10L, 7L, CBU_ORIGEN, new BigDecimal("100000.00"));
        Cuenta destino = cuenta(20L, 8L, CBU_DESTINO, BigDecimal.ZERO);
        when(transferValidator.validar(any(DatosTransferencia.class)))
                .thenReturn(new TransferenciaValidada(origen, destino,
                        Money.ars(new BigDecimal("15000.00"))));
        when(cuentaRepository.save(any(Cuenta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(new TransferirCommand(10L, "  " + CBU_DESTINO.valor() + "  ",
                new BigDecimal("15000.00")), 7L);

        // El trim de cbuDestino vive en DatosTransferencia (normalización en un
        // solo lugar); el use case pasa el clienteId autenticado del claim.
        ArgumentCaptor<DatosTransferencia> datosCaptor = ArgumentCaptor.forClass(DatosTransferencia.class);
        verify(transferValidator).validar(datosCaptor.capture());
        DatosTransferencia datos = datosCaptor.getValue();
        assertEquals(10L, datos.cuentaOrigenId());
        assertEquals(CBU_DESTINO.valor(), datos.cbuDestino());
        assertEquals(new BigDecimal("15000.00"), datos.monto());
        assertEquals(7L, datos.clienteIdClaim());
    }

    @Test
    void validadorRechazaNoSePersisteNadaNiSePublicaElEvento() {
        when(transferValidator.validar(any(DatosTransferencia.class)))
                .thenThrow(new SaldoInsuficienteException());

        assertThrows(SaldoInsuficienteException.class, () ->
                useCase.ejecutar(new TransferirCommand(10L, CBU_DESTINO.valor(),
                        new BigDecimal("15000.00")), 7L));

        // FR-002: rollback total — sin saves de cuentas ni movimientos y sin
        // evento (ERR-001, AC-004).
        verify(cuentaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
        verify(eventPublisher, never()).publicar(any());
    }

    private static Cuenta cuenta(Long id, Long clienteId, CBU cbu, BigDecimal saldo) {
        return new Cuenta(id, clienteId, cbu, TipoCuenta.CAJA_AHORRO, Money.ars(saldo), ARS,
                EstadoCuenta.ACTIVA, CREATED_AT, 0L);
    }
}
