package com.banco.application;

import com.banco.application.query.ListarCuentasQuery;
import com.banco.application.usecase.ListarCuentasUseCase;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.DNI;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Listado de cuentas por rol (AC-018..AC-023, A-004): CLIENTE solo sus
 * cuentas (claim obligatorio, sin parámetro); ADMIN todas o filtradas con
 * validación del cliente.
 */
class ListarCuentasUseCaseTest {

    private CuentaRepository repository;
    private ClienteRepository clienteRepository;
    private ListarCuentasUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(CuentaRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        useCase = new ListarCuentasUseCase(repository, clienteRepository);
    }

    private static Cuenta cuenta(long id, long clienteId) {
        return new Cuenta(id, clienteId, new CBU("0000000100010000000011"), TipoCuenta.CAJA_AHORRO,
                Money.cero(new Moneda("ARS")), new Moneda("ARS"), EstadoCuenta.ACTIVA,
                Instant.parse("2026-08-16T10:00:00Z"), 0L);
    }

    private static Cliente cliente(long id) {
        return new Cliente(id, "Juan", "Perez", new DNI("12345678"), "juan@example.com", null,
                Instant.parse("2026-08-16T10:00:00Z"));
    }

    @Test
    void clienteConClaimListaSoloSusCuentasYNoLlamaFindAll() {
        when(repository.findByClienteId(7L)).thenReturn(List.of(cuenta(1L, 7L), cuenta(2L, 7L)));

        List<Cuenta> resultado = useCase.ejecutar(new ListarCuentasQuery("CLIENTE", 7L, null));

        assertEquals(2, resultado.size());
        verify(repository).findByClienteId(7L);
        verify(repository, never()).findAll();
    }

    @Test
    void clienteSinClaimLanzaAccesoDenegado() {
        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ListarCuentasQuery("CLIENTE", null, null)));

        verify(repository, never()).findAll();
        verify(repository, never()).findByClienteId(any());
    }

    @Test
    void clienteConFiltroLanzaAccesoDenegado() {
        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ListarCuentasQuery("CLIENTE", 7L, 7L)));

        // A-004/AC-019: el CLIENTE no envía el parámetro clienteId.
        verify(repository, never()).findAll();
        verify(repository, never()).findByClienteId(any());
    }

    @Test
    void adminConFiltroValidoListaSoloLasCuentasDeEseCliente() {
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(cliente(7L)));
        when(repository.findByClienteId(7L)).thenReturn(List.of(cuenta(1L, 7L)));

        List<Cuenta> resultado = useCase.ejecutar(new ListarCuentasQuery("ADMIN", null, 7L));

        assertEquals(1, resultado.size());
        verify(clienteRepository).findById(7L);
        verify(repository).findByClienteId(7L);
        verify(repository, never()).findAll();
    }

    @Test
    void adminConFiltroInexistenteLanzaClienteNoEncontrado() {
        when(clienteRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> useCase.ejecutar(new ListarCuentasQuery("ADMIN", null, 7L)));

        verify(repository, never()).findByClienteId(any());
        verify(repository, never()).findAll();
    }

    @Test
    void adminSinFiltroListaTodasLasCuentas() {
        when(repository.findAll()).thenReturn(List.of(cuenta(1L, 7L), cuenta(2L, 8L)));

        List<Cuenta> resultado = useCase.ejecutar(new ListarCuentasQuery("ADMIN", null, null));

        assertEquals(2, resultado.size());
        verify(repository).findAll();
        verify(clienteRepository, never()).findById(any());
    }
}
