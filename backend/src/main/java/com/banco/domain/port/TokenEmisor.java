package com.banco.domain.port;

import com.banco.domain.model.Usuario;

/**
 * Abstracción de la emisión de tokens JWT (ADR-005). Permite que el login sea
 * orquestado por {@code AutenticarUsuarioUseCase} (application) sin depender de
 * infraestructura. Lo implementa {@code JwtService} (única implementación).
 */
public interface TokenEmisor {

    /**
     * Emite un JWT HS256 de expiración corta para el usuario: {@code sub} =
     * username, {@code role} y, solo para {@code CLIENTE}, {@code clienteId}
     * (contrato de claims: ADR-004 §5 / SPEC-003 §6.3).
     */
    String emitir(Usuario usuario);
}
