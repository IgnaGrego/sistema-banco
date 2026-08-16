package com.banco.infrastructure.adapter.web;

import com.banco.application.command.AbrirCuentaCommand;
import com.banco.application.query.ListarCuentasQuery;
import com.banco.application.query.ObtenerCuentaPorCbuQuery;
import com.banco.application.query.ObtenerCuentaQuery;
import com.banco.application.usecase.AbrirCuentaUseCase;
import com.banco.application.usecase.ListarCuentasUseCase;
import com.banco.application.usecase.ObtenerCuentaPorCbuUseCase;
import com.banco.application.usecase.ObtenerCuentaUseCase;
import com.banco.domain.model.Cuenta;
import com.banco.infrastructure.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Coordina los endpoints de cuentas (POST /api/v1/cuentas → 201 + Location;
 * GET /{id}; GET /cbu/{cbu}; GET ?clienteId=). No contiene reglas de negocio:
 * construye commands/queries y delega en los use cases (solo resuelve el
 * sujeto autenticado del SecurityContext — patrón de {@code ClienteController}).
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final AbrirCuentaUseCase abrirCuentaUseCase;
    private final ObtenerCuentaUseCase obtenerCuentaUseCase;
    private final ObtenerCuentaPorCbuUseCase obtenerCuentaPorCbuUseCase;
    private final ListarCuentasUseCase listarCuentasUseCase;

    public CuentaController(AbrirCuentaUseCase abrirCuentaUseCase,
                            ObtenerCuentaUseCase obtenerCuentaUseCase,
                            ObtenerCuentaPorCbuUseCase obtenerCuentaPorCbuUseCase,
                            ListarCuentasUseCase listarCuentasUseCase) {
        this.abrirCuentaUseCase = abrirCuentaUseCase;
        this.obtenerCuentaUseCase = obtenerCuentaUseCase;
        this.obtenerCuentaPorCbuUseCase = obtenerCuentaPorCbuUseCase;
        this.listarCuentasUseCase = listarCuentasUseCase;
    }

    @PostMapping
    public ResponseEntity<CuentaDto> abrir(@RequestBody AbrirCuentaRequest request) {
        Cuenta cuenta = abrirCuentaUseCase.ejecutar(new AbrirCuentaCommand(
                request.clienteId(), request.tipo(), request.moneda()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(cuenta.getId())
                .toUri();
        return ResponseEntity.created(location).body(CuentaDto.from(cuenta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuentaDto> obtener(@PathVariable Long id) {
        AuthenticatedUser usuario = usuarioAutenticado();
        Cuenta cuenta = obtenerCuentaUseCase.ejecutar(
                new ObtenerCuentaQuery(id, usuario.rol(), usuario.clienteId()));
        return ResponseEntity.ok(CuentaDto.from(cuenta));
    }

    @GetMapping("/cbu/{cbu}")
    public ResponseEntity<CuentaDto> obtenerPorCbu(@PathVariable String cbu) {
        AuthenticatedUser usuario = usuarioAutenticado();
        Cuenta cuenta = obtenerCuentaPorCbuUseCase.ejecutar(
                new ObtenerCuentaPorCbuQuery(cbu, usuario.rol(), usuario.clienteId()));
        return ResponseEntity.ok(CuentaDto.from(cuenta));
    }

    @GetMapping
    public ResponseEntity<List<CuentaDto>> listar(@RequestParam(required = false) Long clienteId) {
        AuthenticatedUser usuario = usuarioAutenticado();
        List<CuentaDto> cuentas = listarCuentasUseCase.ejecutar(
                        new ListarCuentasQuery(usuario.rol(), usuario.clienteId(), clienteId))
                .stream()
                .map(CuentaDto::from)
                .toList();
        return ResponseEntity.ok(cuentas);
    }

    private AuthenticatedUser usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
