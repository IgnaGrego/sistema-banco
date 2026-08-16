package com.banco.infrastructure.adapter.web;

import com.banco.application.command.TransferirCommand;
import com.banco.application.usecase.TransferenciaConfirmacion;
import com.banco.infrastructure.security.AuthenticatedUser;
import com.banco.infrastructure.service.TransferenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coordina {@code POST /api/v1/transferencias} (FR-001): construye el command,
 * resuelve el {@link AuthenticatedUser} del SecurityContext (mismo mecanismo
 * que {@code ClienteController}) y delega en {@link TransferenciaService}.
 * Sin reglas de negocio. Sin {@code Location} header (no existe
 * {@code GET /transferencias/{id}}; la spec no lo exige — misma decisión que
 * {@code AuthController}).
 */
@RestController
@RequestMapping("/api/v1/transferencias")
public class TransferenciaController {

    private final TransferenciaService transferenciaService;

    public TransferenciaController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping
    public ResponseEntity<TransferenciaConfirmacion> transferir(@RequestBody TransferirRequest request) {
        AuthenticatedUser usuario = usuarioAutenticado();
        TransferenciaConfirmacion confirmacion = transferenciaService.ejecutar(
                new TransferirCommand(request.cuentaOrigenId(), request.cbuDestino(), request.monto()),
                usuario.clienteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(confirmacion);
    }

    private AuthenticatedUser usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
