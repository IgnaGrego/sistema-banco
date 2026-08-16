package com.banco.infrastructure.adapter.web;

import com.banco.application.command.RealizarDepositoCommand;
import com.banco.application.usecase.DepositoConfirmacion;
import com.banco.infrastructure.security.AuthenticatedUser;
import com.banco.infrastructure.service.DepositoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coordina {@code POST /api/v1/depositos} (FR-001): construye el command,
 * resuelve el {@link AuthenticatedUser} del SecurityContext (mismo mecanismo
 * que {@code TransferenciaController}) y delega en {@link DepositoService}.
 * Sin reglas de negocio (la autorización ADMIN se verifica en el matcher y en
 * {@code DepositoRetiroValidator} — A-001). Sin {@code Location} header (no
 * existe {@code GET /depositos/{id}}; misma decisión que
 * {@code TransferenciaController}).
 */
@RestController
@RequestMapping("/api/v1/depositos")
public class DepositoController {

    private final DepositoService depositoService;

    public DepositoController(DepositoService depositoService) {
        this.depositoService = depositoService;
    }

    @PostMapping
    public ResponseEntity<DepositoConfirmacion> depositar(@RequestBody DepositoRequest request) {
        AuthenticatedUser usuario = usuarioAutenticado();
        DepositoConfirmacion confirmacion = depositoService.ejecutar(
                new RealizarDepositoCommand(request.cuentaId(), request.monto(),
                        usuario.rol(), usuario.clienteId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(confirmacion);
    }

    private AuthenticatedUser usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
