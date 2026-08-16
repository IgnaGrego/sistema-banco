package com.banco.application;

import com.banco.application.command.LoginCommand;
import com.banco.application.usecase.AutenticarUsuarioUseCase;
import com.banco.domain.exception.CredencialesInvalidasException;
import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import com.banco.domain.port.PasswordHasher;
import com.banco.domain.port.TokenEmisor;
import com.banco.domain.port.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login (AC-008 lógica, AC-009, AC-010, AC-011): happy path (matches true →
 * TokenEmisor.emitir y su resultado), password incorrecta y username inexistente
 * → MISMA excepción (A-004), guard defensivo sin tocar el repositorio, y
 * verificación vía matches (nunca comparación en claro).
 */
class AutenticarUsuarioUseCaseTest {

    private UsuarioRepository usuarioRepository;
    private PasswordHasher passwordHasher;
    private TokenEmisor tokenEmisor;
    private AutenticarUsuarioUseCase useCase;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        tokenEmisor = mock(TokenEmisor.class);
        useCase = new AutenticarUsuarioUseCase(usuarioRepository, passwordHasher, tokenEmisor);
    }

    private static Usuario usuarioStored() {
        return new Usuario(1L, "juan", "bcrypt-hash-60", Rol.CLIENTE, 7L);
    }

    @Test
    void happyPathDevuelveElJwtEmitido() {
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(usuarioStored()));
        when(passwordHasher.matches("password123", "bcrypt-hash-60")).thenReturn(true);
        when(tokenEmisor.emitir(usuarioStored())).thenReturn("jwt-firmado");

        String token = useCase.ejecutar(new LoginCommand("juan", "password123"));

        assertEquals("jwt-firmado", token);
        verify(tokenEmisor).emitir(usuarioStored());
    }

    @Test
    void passwordIncorrectaLanzaCredencialesInvalidas() {
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(usuarioStored()));
        when(passwordHasher.matches("incorrecta", "bcrypt-hash-60")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> useCase.ejecutar(new LoginCommand("juan", "incorrecta")));
        verify(tokenEmisor, never()).emitir(any());
    }

    @Test
    void usernameInexistenteLanzaLaMismaExcepcion() {
        when(usuarioRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class,
                () -> useCase.ejecutar(new LoginCommand("ghost", "password123")));
        verify(tokenEmisor, never()).emitir(any());
    }

    @Test
    void usernameBlankNoTocaElRepositorio() {
        assertThrows(CredencialesInvalidasException.class,
                () -> useCase.ejecutar(new LoginCommand("   ", "password123")));
        verify(usuarioRepository, never()).findByUsername(anyString());
    }

    @Test
    void passwordNullNoTocaElRepositorio() {
        assertThrows(CredencialesInvalidasException.class,
                () -> useCase.ejecutar(new LoginCommand("juan", null)));
        verify(usuarioRepository, never()).findByUsername(anyString());
    }

    @Test
    void elUsernameSeNormalizaConTrim() {
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(usuarioStored()));
        when(passwordHasher.matches("password123", "bcrypt-hash-60")).thenReturn(true);
        when(tokenEmisor.emitir(usuarioStored())).thenReturn("jwt-firmado");

        useCase.ejecutar(new LoginCommand("  juan  ", "password123"));

        verify(usuarioRepository).findByUsername("juan");
    }

    @Test
    void matchesRecibeLaPasswordEnClaroYElHashAlmacenado() {
        // AC-011: la verificación usa BCrypt matches(rawPassword, hash);
        // nunca se compara la password en claro con el hash ni viceversa.
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(usuarioStored()));
        when(passwordHasher.matches("password123", "bcrypt-hash-60")).thenReturn(true);
        when(tokenEmisor.emitir(usuarioStored())).thenReturn("jwt-firmado");

        useCase.ejecutar(new LoginCommand("juan", "password123"));

        verify(passwordHasher).matches("password123", "bcrypt-hash-60");
    }
}
