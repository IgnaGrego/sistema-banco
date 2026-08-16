package com.banco.application;

import com.banco.application.command.AbrirCuentaCommand;
import com.banco.application.usecase.AbrirCuentaUseCase;
import com.banco.application.validator.AperturaValidator;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.MonedaNoSoportadaException;
import com.banco.domain.model.Cliente;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Apertura de cuenta (AC-001 lógica, AC-002, AC-003, AC-005, AC-028): default
 * de moneda ARS, rechazo de no-ARS, titular inexistente sin persistir,
 * regeneración de CBU ante colisión y validador invocado antes que los
 * chequeos de repositorio.
 */
class AbrirCuentaUseCaseTest {

    private CuentaRepository repository;
    private ClienteRepository clienteRepository;
    private AperturaValidator validator;
    private AbrirCuentaUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(CuentaRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        validator = mock(AperturaValidator.class);
        useCase = new AbrirCuentaUseCase(repository, clienteRepository, validator);
    }

    private static AbrirCuentaCommand command(String tipo, String moneda) {
        return new AbrirCuentaCommand(7L, tipo, moneda);
    }

    private void stubClienteExistente() {
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(cliente()));
    }

    private static Cliente cliente() {
        return new Cliente(7L, "Juan", "Perez", new DNI("12345678"), "juan@example.com", null,
                Instant.parse("2026-08-16T10:00:00Z"));
    }

    @Test
    void happyPathCajaAhorroSinMonedaUsaArsPorDefecto() {
        stubClienteExistente();
        when(repository.existsByCbu(any(CBU.class))).thenReturn(false);
        when(repository.save(any(Cuenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cuenta resultado = useCase.ejecutar(command("CAJA_AHORRO", null));

        ArgumentCaptor<Cuenta> captor = ArgumentCaptor.forClass(Cuenta.class);
        verify(repository).save(captor.capture());
        Cuenta guardada = captor.getValue();

        // FR-001/FR-008/A-005: sin moneda → ARS por defecto.
        assertEquals("ARS", guardada.getMoneda().codigo());
        assertNull(guardada.getId()); // la BD asigna el id
        assertEquals(7L, guardada.getClienteId());
        assertEquals(TipoCuenta.CAJA_AHORRO, guardada.getTipo());
        assertEquals(0, guardada.getSaldo().monto().signum());
        assertEquals(EstadoCuenta.ACTIVA, guardada.getEstado());
        assertEquals(22, guardada.getCbu().valor().length()); // FR-002
        assertNull(resultado.getId());
        verify(clienteRepository).findById(7L);
        verify(validator).validar("CAJA_AHORRO", null);
    }

    @Test
    void happyPathCuentaCorrienteConArsExplicito() {
        stubClienteExistente();
        when(repository.existsByCbu(any(CBU.class))).thenReturn(false);
        when(repository.save(any(Cuenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(command("CUENTA_CORRIENTE", "ARS"));

        ArgumentCaptor<Cuenta> captor = ArgumentCaptor.forClass(Cuenta.class);
        verify(repository).save(captor.capture());
        assertEquals(TipoCuenta.CUENTA_CORRIENTE, captor.getValue().getTipo());
        assertEquals("ARS", captor.getValue().getMoneda().codigo());
    }

    @Test
    void monedaUsdLanzaMonedaNoSoportadaYNoPersiste() {
        assertThrows(MonedaNoSoportadaException.class,
                () -> useCase.ejecutar(command("CAJA_AHORRO", "USD")));

        // BR-005/A-005: el rechazo ocurre antes del chequeo del titular y del save.
        verify(clienteRepository, never()).findById(any());
        verify(repository, never()).existsByCbu(any(CBU.class));
        verify(repository, never()).save(any());
    }

    @Test
    void clienteInexistenteLanzaClienteNoEncontradoYNoPersiste() {
        when(clienteRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> useCase.ejecutar(command("CAJA_AHORRO", "ARS")));

        // ERR-002/AC-005: no se genera CBU ni se persiste.
        verify(repository, never()).existsByCbu(any(CBU.class));
        verify(repository, never()).save(any());
    }

    @Test
    void regeneraCbuAnteColisionYPersisteUnaSolaVezConElSegundoCbu() {
        stubClienteExistente();
        when(repository.existsByCbu(any(CBU.class))).thenReturn(true, false);
        when(repository.save(any(Cuenta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(command("CAJA_AHORRO", "ARS"));

        // AC-028: dos CBUs distintos (el primero colisiona, el segundo pasa).
        ArgumentCaptor<CBU> cbuCaptor = ArgumentCaptor.forClass(CBU.class);
        verify(repository, times(2)).existsByCbu(cbuCaptor.capture());
        List<CBU> cbus = cbuCaptor.getAllValues();
        assertEquals(2, cbus.size());
        assertNotEquals(cbus.get(0), cbus.get(1));
        assertEquals(22, cbus.get(0).valor().length());
        assertEquals(22, cbus.get(1).valor().length());

        // Se persiste exactamente una vez, con el segundo CBU.
        ArgumentCaptor<Cuenta> cuentaCaptor = ArgumentCaptor.forClass(Cuenta.class);
        verify(repository).save(cuentaCaptor.capture());
        assertEquals(cbus.get(1), cuentaCaptor.getValue().getCbu());
    }

    @Test
    void validadorSeInvocaAntesDeLosChequeosDeRepositorio() {
        doThrow(new DatosInvalidosException("tipo", "invalido"))
                .when(validator).validar(anyString(), anyString());

        assertThrows(DatosInvalidosException.class,
                () -> useCase.ejecutar(command("AHORRO", "ARS")));

        verify(clienteRepository, never()).findById(any());
        verify(repository, never()).existsByCbu(any(CBU.class));
        verify(repository, never()).save(any());
    }
}
