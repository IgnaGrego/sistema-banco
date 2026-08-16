package com.banco.application;

import com.banco.application.command.ActualizarClienteCommand;
import com.banco.application.usecase.ActualizarClienteUseCase;
import com.banco.application.validator.ClienteValidator;
import com.banco.application.validator.DatosCliente;
import com.banco.domain.exception.ClienteDuplicadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Edición de cliente, PUT total (AC-014 lógica, AC-015, AC-016, AC-017,
 * AC-018).
 */
class ActualizarClienteUseCaseTest {

    private static final Instant FECHA_ALTA = Instant.parse("2026-08-16T10:00:00Z");

    private ClienteRepository repository;
    private ClienteValidator validator;
    private ActualizarClienteUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ClienteRepository.class);
        validator = mock(ClienteValidator.class);
        useCase = new ActualizarClienteUseCase(repository, validator);
    }

    private static Cliente clienteExistente() {
        return new Cliente(1L, "Juan", "Perez", new DNI("12345678"),
                "juan@example.com", null, FECHA_ALTA);
    }

    private static ActualizarClienteCommand commandValido() {
        return new ActualizarClienteCommand(1L, "Maria", "Gomez", "87654321",
                "maria@example.com", "+549112345678");
    }

    @Test
    void happyPathActualizaYPersisteSinTocarIdNiFechaAlta() {
        Cliente existente = clienteExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.existsByDniAndIdNot(any(DNI.class), eq(1L))).thenReturn(false);
        when(repository.existsByEmailAndIdNot(any(String.class), eq(1L))).thenReturn(false);
        when(repository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente resultado = useCase.ejecutar(commandValido());

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(repository).save(captor.capture());
        Cliente guardado = captor.getValue();

        assertEquals(1L, guardado.getId());
        assertEquals(FECHA_ALTA, guardado.getFechaAlta()); // no editable (FR-003)
        assertEquals("Maria", guardado.getNombre());
        assertEquals("Gomez", guardado.getApellido());
        assertEquals("87654321", guardado.getDni().valor());
        assertEquals("maria@example.com", guardado.getEmail());
        assertEquals("+549112345678", guardado.getTelefono());
        assertEquals(resultado, guardado);
    }

    @Test
    void idInexistenteLanzaClienteNoEncontrado() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> useCase.ejecutar(new ActualizarClienteCommand(99L, "Maria", "Gomez",
                        "87654321", "maria@example.com", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void dniDuplicadoDeOtroClienteLanzaClienteDuplicadoConCampoDni() {
        when(repository.findById(1L)).thenReturn(Optional.of(clienteExistente()));
        when(repository.existsByDniAndIdNot(any(DNI.class), eq(1L))).thenReturn(true);

        ClienteDuplicadoException e = assertThrows(ClienteDuplicadoException.class,
                () -> useCase.ejecutar(commandValido()));
        assertEquals("dni", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void emailDuplicadoDeOtroClienteLanzaClienteDuplicadoConCampoEmail() {
        when(repository.findById(1L)).thenReturn(Optional.of(clienteExistente()));
        when(repository.existsByDniAndIdNot(any(DNI.class), eq(1L))).thenReturn(false);
        when(repository.existsByEmailAndIdNot(any(String.class), eq(1L))).thenReturn(true);

        ClienteDuplicadoException e = assertThrows(ClienteDuplicadoException.class,
                () -> useCase.ejecutar(commandValido()));
        assertEquals("email", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void mantenerPropioDniYEmailEsValido() {
        Cliente existente = clienteExistente();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        // El propio cliente se excluye: los chequeos AndIdNot devuelven false.
        when(repository.existsByDniAndIdNot(any(DNI.class), eq(1L))).thenReturn(false);
        when(repository.existsByEmailAndIdNot(any(String.class), eq(1L))).thenReturn(false);
        when(repository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActualizarClienteCommand mantenerPropio = new ActualizarClienteCommand(1L,
                "Juan", "Perez", "12345678", "juan@example.com", null);

        Cliente resultado = useCase.ejecutar(mantenerPropio);

        assertEquals("12345678", resultado.getDni().valor());
        assertEquals("juan@example.com", resultado.getEmail());
        verify(repository).existsByDniAndIdNot(any(DNI.class), eq(1L));
        verify(repository).existsByEmailAndIdNot(any(String.class), eq(1L));
    }

    @Test
    void validadorSeInvocaAntesDelChequeoDeUnicidad() {
        when(repository.findById(1L)).thenReturn(Optional.of(clienteExistente()));
        doThrow(new com.banco.domain.exception.DatosInvalidosException("email", "invalido"))
                .when(validator).validar(any(DatosCliente.class));

        assertThrows(com.banco.domain.exception.DatosInvalidosException.class,
                () -> useCase.ejecutar(commandValido()));
        verify(repository, never()).existsByDniAndIdNot(any(DNI.class), eq(1L));
        verify(repository, never()).save(any());
    }
}
