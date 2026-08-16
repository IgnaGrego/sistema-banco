package com.banco.application;

import com.banco.application.query.ObtenerClienteQuery;
import com.banco.application.usecase.ObtenerClienteUseCase;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;
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
 * Consulta de cliente con verificación de propiedad para rol CLIENTE
 * (AC-010, AC-011, AC-012, AC-013).
 */
class ObtenerClienteUseCaseTest {

    private ClienteRepository repository;
    private ObtenerClienteUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ClienteRepository.class);
        useCase = new ObtenerClienteUseCase(repository);
    }

    private static Cliente cliente() {
        return new Cliente(7L, "Juan", "Perez", new DNI("12345678"),
                "juan@example.com", null, Instant.parse("2026-08-16T10:00:00Z"));
    }

    @Test
    void adminConsultaCualquierCliente() {
        when(repository.findById(7L)).thenReturn(Optional.of(cliente()));

        Cliente resultado = useCase.ejecutar(new ObtenerClienteQuery(7L, "ADMIN", null));

        assertEquals(7L, resultado.getId());
    }

    @Test
    void clienteConsultaSuPropioId() {
        when(repository.findById(7L)).thenReturn(Optional.of(cliente()));

        Cliente resultado = useCase.ejecutar(new ObtenerClienteQuery(7L, "CLIENTE", 7L));

        assertEquals(7L, resultado.getId());
    }

    @Test
    void clienteConsultaIdAjenoLanzaAccesoDenegado() {
        when(repository.findById(7L)).thenReturn(Optional.of(cliente()));

        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ObtenerClienteQuery(7L, "CLIENTE", 8L)));
        verify(repository, never()).findById(any());
    }

    @Test
    void clienteSinClaimLanzaAccesoDenegado() {
        assertThrows(AccesoDenegadoException.class,
                () -> useCase.ejecutar(new ObtenerClienteQuery(7L, "CLIENTE", null)));
        verify(repository, never()).findById(any());
    }

    @Test
    void idInexistenteLanzaClienteNoEncontrado() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> useCase.ejecutar(new ObtenerClienteQuery(99L, "ADMIN", null)));
    }
}
