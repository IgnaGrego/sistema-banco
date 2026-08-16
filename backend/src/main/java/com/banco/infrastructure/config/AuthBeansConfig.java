package com.banco.infrastructure.config;

import com.banco.application.usecase.AutenticarUsuarioUseCase;
import com.banco.application.usecase.RegistrarUsuarioUseCase;
import com.banco.application.validator.RegistroValidator;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.PasswordHasher;
import com.banco.domain.port.TokenEmisor;
import com.banco.domain.port.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wiring de la autenticación (SPEC-003). Mantiene el paquete application libre
 * de Spring (regla ArchUnit): los use cases reciben puertos de dominio.
 *
 * {@code PasswordEncoder} (BCrypt) lo consume {@code BcryptPasswordHasher}
 * (infrastructure.security, {@code @Component} que implementa
 * {@link PasswordHasher}); {@link TokenEmisor} se resuelve automáticamente al
 * bean {@code JwtService} (único {@code @Component} que lo implementa).
 */
@Configuration
public class AuthBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt, cost factor default (sin configuración adicional — SPEC-003 §8.7).
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RegistroValidator registroValidator() {
        return new RegistroValidator();
    }

    @Bean
    public RegistrarUsuarioUseCase registrarUsuarioUseCase(UsuarioRepository usuarioRepository,
                                                           ClienteRepository clienteRepository,
                                                           PasswordHasher passwordHasher,
                                                           RegistroValidator registroValidator) {
        return new RegistrarUsuarioUseCase(usuarioRepository, clienteRepository,
                passwordHasher, registroValidator);
    }

    @Bean
    public AutenticarUsuarioUseCase autenticarUsuarioUseCase(UsuarioRepository usuarioRepository,
                                                             PasswordHasher passwordHasher,
                                                             TokenEmisor tokenEmisor) {
        return new AutenticarUsuarioUseCase(usuarioRepository, passwordHasher, tokenEmisor);
    }
}
