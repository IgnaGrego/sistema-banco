package com.banco.infrastructure.adapter.web;

import com.banco.application.command.RealizarRetiroCommand;
import com.banco.application.usecase.RetiroConfirmacion;
import com.banco.infrastructure.security.AuthenticatedUser;
import com.banco.infrastructure.service.RetiroService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coordina {@code POST /api/v1/retiros} (FR-002): construye el command,
 * resuelve el {@link AuthenticatedUser} del SecurityContext (mismo mecanismo
 * que {@code TransferenciaController}) y delega en {@link RetiroService}.
 * Sin reglas de negocio (la autorización se verifica en el matcher y la
 * propiedad de CLIENTE en {@code DepositoRetiroValidator} — §9). Sin
 * {@code Location} header (no existe {@code GET /retiros/{id}}; misma decisión
 * que {@code TransferenciaController}).
 */
@RestController
@RequestMapping("/api/v1/retiros")
public class RetiroController {

    private final RetiroService retiroService;

    public RetiroController(RetiroService retiroService) {
        this.retiroService = retiroService;
    }

    @PostMapping
    public ResponseEntity<RetiroConfirmacion> retirar(@RequestBody RetiroRequest request) {
        AuthenticatedUser usuario = usuarioAutenticado();
        RetiroConfirmacion confirmacion = retiroService.ejecutar(
                new RealizarRetiroCommand(request.cuentaId(), request.monto(),
                        usuario.rol(), usuario.clienteId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(confirmacion);
    }

    private AuthenticatedUser usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
