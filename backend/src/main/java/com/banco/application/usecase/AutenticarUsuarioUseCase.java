package com.banco.application.usecase;

import com.banco.application.command.LoginCommand;
import com.banco.domain.exception.CredencialesInvalidasException;
import com.banco.domain.model.Usuario;
import com.banco.domain.port.PasswordHasher;
import com.banco.domain.port.TokenEmisor;
import com.banco.domain.port.UsuarioRepository;

import java.util.Optional;

/**
 * Login (FR-002, main flow): verifica credenciales y emite el JWT vía el puerto
 * {@link TokenEmisor} (el use case orquesta la operación completa — ADR-005).
 *
 * La respuesta 401 es idéntica para username inexistente y password incorrecta
 * (A-004, ERR-001). La password nunca se recorta ni se compara en claro: la
 * verificación usa {@code PasswordHasher.matches} contra el hash BCrypt
 * almacenado (BR-001, AC-011).
 *
 * Java puro (sin Spring): los beans los declara AuthBeansConfig.
 */
public class AutenticarUsuarioUseCase {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;
    private final TokenEmisor tokenEmisor;

    public AutenticarUsuarioUseCase(UsuarioRepository usuarioRepository,
                                    PasswordHasher passwordHasher,
                                    TokenEmisor tokenEmisor) {
        this.usuarioRepository = usuarioRepository;
        this.passwordHasher = passwordHasher;
        this.tokenEmisor = tokenEmisor;
    }

    public String ejecutar(LoginCommand command) {
        String username = command.username() == null ? null : command.username().trim();
        String password = command.password(); // NUNCA se recorta la password

        if (username == null || username.isBlank() || password == null) {
            // Guard defensivo: 401 idéntico al resto de los casos (A-004).
            throw new CredencialesInvalidasException();
        }

        Optional<Usuario> opt = usuarioRepository.findByUsername(username);
        if (opt.isEmpty()) {
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = opt.get();
        if (!passwordHasher.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        return tokenEmisor.emitir(usuario);
    }
}
