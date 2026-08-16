package com.banco.application;

import com.banco.application.command.RegistrarUsuarioCommand;
import com.banco.application.usecase.RegistrarUsuarioUseCase;
import com.banco.application.validator.DatosRegistro;
import com.banco.application.validator.RegistroValidator;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.UsernameDuplicadoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.PasswordHasher;
import com.banco.domain.port.UsuarioRepository;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registro de usuario (AC-001, AC-002, AC-004, AC-006 lógica, AC-007, AC-018):
 * happy paths CLIENTE/ADMIN, unicidad (ERR-005), cliente inexistente (ERR-006),
 * validador ANTES de unicidad, ADMIN ignora clienteId y hash ≠ password en claro.
 */
class RegistrarUsuarioUseCaseTest {

    private UsuarioRepository usuarioRepository;
    private ClienteRepository clienteRepository;
    private PasswordHasher passwordHasher;
    private RegistroValidator validator;
    private RegistrarUsuarioUseCase useCase;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        validator = mock(RegistroValidator.class);
        useCase = new RegistrarUsuarioUseCase(usuarioRepository, clienteRepository, passwordHasher, validator);
    }

    private static Cliente clienteExistente() {
        return new Cliente(7L, "Juan", "Perez", new DNI("12345678"),
                "juan@example.com", null, Instant.parse("2026-08-16T10:00:00Z"));
    }

    @Test
    void happyPathCLIENTEPersisteConHashYClienteIdVinculado() {
        when(usuarioRepository.existsByUsername(anyString())).thenReturn(false);
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(clienteExistente()));
        when(passwordHasher.hash("password123")).thenReturn("bcrypt-hash-60");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Usuario resultado = useCase.ejecutar(new RegistrarUsuarioCommand("juan", "password123", "CLIENTE", 7L));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertNull(guardado.getId()); // la BD asigna el id
        assertEquals("juan", guardado.getUsername());
        assertEquals("bcrypt-hash-60", guardado.getPasswordHash());
        assertEquals(Rol.CLIENTE, guardado.getRol());
        assertEquals(7L, guardado.getClienteId());
        assertNotNull(resultado);
        verify(validator).validar(any(DatosRegistro.class));
    }

    @Test
    void happyPathADMINSinClienteIdYNoConsultaClientes() {
        when(usuarioRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordHasher.hash("password123")).thenReturn("bcrypt-hash-60");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(new RegistrarUsuarioCommand("admin", "password123", "ADMIN", null));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals(Rol.ADMIN, guardado.getRol());
        assertNull(guardado.getClienteId());
        // Para ADMIN no se verifica ningún cliente (ERR-006 solo aplica a CLIENTE).
        verify(clienteRepository, never()).findById(anyLong());
    }

    @Test
    void adminConClienteIdInformadoLoIgnoraYPersisteNull() {
        when(usuarioRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordHasher.hash("password123")).thenReturn("bcrypt-hash-60");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(new RegistrarUsuarioCommand("admin", "password123", "ADMIN", 99L));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNull(captor.getValue().getClienteId());
        verify(clienteRepository, never()).findById(anyLong());
    }

    @Test
    void usernameDuplicadoLanzaUsernameDuplicadoYSaveNoSeInvoca() {
        when(usuarioRepository.existsByUsername(anyString())).thenReturn(true);

        UsernameDuplicadoException e = assertThrows(UsernameDuplicadoException.class,
                () -> useCase.ejecutar(new RegistrarUsuarioCommand("juan", "password123", "CLIENTE", 7L)));

        assertEquals("username", e.getCampo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void clienteInexistenteLanzaClienteNoEncontradoYSaveNoSeInvoca() {
        when(usuarioRepository.existsByUsername(anyString())).thenReturn(false);
        when(clienteRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> useCase.ejecutar(new RegistrarUsuarioCommand("juan", "password123", "CLIENTE", 777L)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void validadorSeInvocaAntesDelChequeoDeUnicidad() {
        doThrow(new DatosInvalidosException("password", "corta"))
                .when(validator).validar(any(DatosRegistro.class));

        assertThrows(DatosInvalidosException.class,
                () -> useCase.ejecutar(new RegistrarUsuarioCommand("juan", "123", "CLIENTE", 7L)));
        verify(usuarioRepository, never()).existsByUsername(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void laPasswordSePersisteHasheadaYNuncaEnClaro() {
        when(usuarioRepository.existsByUsername(anyString())).thenReturn(false);
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(clienteExistente()));
        when(passwordHasher.hash("password123")).thenReturn("bcrypt-hash-60");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.ejecutar(new RegistrarUsuarioCommand("juan", "password123", "CLIENTE", 7L));

        // El use case hashea la password en claro y persiste SOLO el hash (BR-001, AC-007).
        verify(passwordHasher).hash("password123");
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("bcrypt-hash-60", captor.getValue().getPasswordHash());
    }
}
