package com.banco.infrastructure.adapter.web;

import com.banco.application.command.LoginCommand;
import com.banco.application.command.RegistrarUsuarioCommand;
import com.banco.application.usecase.AutenticarUsuarioUseCase;
import com.banco.application.usecase.RegistrarUsuarioUseCase;
import com.banco.domain.model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de autenticación (FR-001, FR-002). Sin reglas de negocio:
 * construye commands y delega en los use cases; mapea resultados a DTOs.
 * El registro responde 201 con {@link UsuarioDto} (sin Location: no existe un
 * GET /usuarios/{id}; la spec no lo exige — docs/architecture/SPEC-003.md §5.1).
 * El login responde 200 con {@link LoginResponse}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrarUsuarioUseCase registrarUsuarioUseCase;
    private final AutenticarUsuarioUseCase autenticarUsuarioUseCase;

    public AuthController(RegistrarUsuarioUseCase registrarUsuarioUseCase,
                          AutenticarUsuarioUseCase autenticarUsuarioUseCase) {
        this.registrarUsuarioUseCase = registrarUsuarioUseCase;
        this.autenticarUsuarioUseCase = autenticarUsuarioUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<UsuarioDto> registrar(@RequestBody RegistrarUsuarioRequest request) {
        Usuario usuario = registrarUsuarioUseCase.ejecutar(new RegistrarUsuarioCommand(
                request.username(), request.password(), request.rol(), request.clienteId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioDto.from(usuario));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = autenticarUsuarioUseCase.ejecutar(
                new LoginCommand(request.username(), request.password()));
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
