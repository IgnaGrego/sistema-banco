package com.banco.infrastructure.adapter.web;

import com.banco.application.query.ObtenerMovimientosQuery;
import com.banco.application.usecase.ObtenerMovimientosUseCase;
import com.banco.domain.model.Movimiento;
import com.banco.infrastructure.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Coordina {@code GET /api/v1/cuentas/{id}/movimientos} (FR-005): construye el
 * query con el sujeto autenticado y delega en
 * {@link ObtenerMovimientosUseCase}. Sin reglas de negocio (la propiedad se
 * verifica en el use case — A-005).
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class MovimientoController {

    private final ObtenerMovimientosUseCase obtenerMovimientosUseCase;

    public MovimientoController(ObtenerMovimientosUseCase obtenerMovimientosUseCase) {
        this.obtenerMovimientosUseCase = obtenerMovimientosUseCase;
    }

    @GetMapping("/{id}/movimientos")
    public ResponseEntity<List<MovimientoDto>> movimientos(@PathVariable Long id) {
        AuthenticatedUser usuario = usuarioAutenticado();
        List<MovimientoDto> movimientos = obtenerMovimientosUseCase.ejecutar(
                        new ObtenerMovimientosQuery(id, usuario.rol(), usuario.clienteId()))
                .stream()
                .map(MovimientoDto::from)
                .toList();
        return ResponseEntity.ok(movimientos);
    }

    private AuthenticatedUser usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
