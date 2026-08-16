package com.banco.application;

import com.banco.application.query.ListarClientesQuery;
import com.banco.application.usecase.ListarClientesUseCase;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Listado de clientes (AC-020 lógica): delega en repository.findAll().
 */
class ListarClientesUseCaseTest {

    private ClienteRepository repository;
    private ListarClientesUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ClienteRepository.class);
        useCase = new ListarClientesUseCase(repository);
    }

    @Test
    void delegaEnFindAllYDevuelveLaLista() {
        List<Cliente> clientes = List.of(
                new Cliente(1L, "Ana", "Lopez", new DNI("12345678"), "ana@example.com", null,
                        Instant.parse("2026-08-16T10:00:00Z")),
                new Cliente(2L, "Juan", "Perez", new DNI("87654321"), "juan@example.com", null,
                        Instant.parse("2026-08-16T10:00:00Z")));
        when(repository.findAll()).thenReturn(clientes);

        List<Cliente> resultado = useCase.ejecutar(new ListarClientesQuery());

        assertEquals(clientes, resultado);
        verify(repository).findAll();
    }
}
