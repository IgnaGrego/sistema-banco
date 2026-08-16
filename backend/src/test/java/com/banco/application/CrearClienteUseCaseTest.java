package com.banco.application;

import com.banco.application.command.CrearClienteCommand;
import com.banco.application.usecase.CrearClienteUseCase;
import com.banco.application.validator.ClienteValidator;
import com.banco.application.validator.DatosCliente;
import com.banco.domain.exception.ClienteDuplicadoException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alta de cliente (AC-001 lógica, AC-002, AC-003).
 */
class CrearClienteUseCaseTest {

    private ClienteRepository repository;
    private ClienteValidator validator;
    private CrearClienteUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ClienteRepository.class);
        validator = mock(ClienteValidator.class);
        useCase = new CrearClienteUseCase(repository, validator);
    }

    private static CrearClienteCommand commandValido() {
        return new CrearClienteCommand("Juan", "Perez", "12345678", "juan@example.com", "+549112345678");
    }

    @Test
    void happyPathCreaClienteConFechaAltaYPersiste() {
        when(repository.existsByDni(any(DNI.class))).thenReturn(false);
        when(repository.existsByEmail(any(String.class))).thenReturn(false);
        when(repository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente resultado = useCase.ejecutar(commandValido());

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(repository).save(captor.capture());
        Cliente guardado = captor.getValue();

        assertNull(guardado.getId()); // la BD asigna el id
        assertNotNull(guardado.getFechaAlta()); // fechaAlta automática (FR-005)
        assertNotNull(resultado.getFechaAlta());
        assertEquals("Juan", guardado.getNombre());
        assertEquals("juan@example.com", guardado.getEmail());
        verify(validator).validar(any(DatosCliente.class));
    }

    @Test
    void dniDuplicadoLanzaClienteDuplicadoConCampoDni() {
        when(repository.existsByDni(any(DNI.class))).thenReturn(true);

        ClienteDuplicadoException e = assertThrows(ClienteDuplicadoException.class,
                () -> useCase.ejecutar(commandValido()));
        assertEquals("dni", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void emailDuplicadoLanzaClienteDuplicadoConCampoEmail() {
        when(repository.existsByDni(any(DNI.class))).thenReturn(false);
        when(repository.existsByEmail(any(String.class))).thenReturn(true);

        ClienteDuplicadoException e = assertThrows(ClienteDuplicadoException.class,
                () -> useCase.ejecutar(commandValido()));
        assertEquals("email", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void validadorSeInvocaAntesDelChequeoDeUnicidad() {
        doThrow(new DatosInvalidosException("dni", "invalido"))
                .when(validator).validar(any(DatosCliente.class));

        assertThrows(DatosInvalidosException.class, () -> useCase.ejecutar(commandValido()));
        verify(repository, never()).existsByDni(any(DNI.class));
        verify(repository, never()).save(any());
    }
}
