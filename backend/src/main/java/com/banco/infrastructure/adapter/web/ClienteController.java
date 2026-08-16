package com.banco.infrastructure.adapter.web;

import com.banco.application.command.ActualizarClienteCommand;
import com.banco.application.command.CrearClienteCommand;
import com.banco.application.query.ListarClientesQuery;
import com.banco.application.query.ObtenerClienteQuery;
import com.banco.application.usecase.ActualizarClienteUseCase;
import com.banco.application.usecase.CrearClienteUseCase;
import com.banco.application.usecase.ListarClientesUseCase;
import com.banco.application.usecase.ObtenerClienteUseCase;
import com.banco.domain.model.Cliente;
import com.banco.infrastructure.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Coordina los endpoints de clientes. No contiene reglas de negocio: construye
 * commands/queries y delega en los use cases (solo resuelve el sujeto
 * autenticado del SecurityContext para la consulta).
 */
@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final CrearClienteUseCase crearClienteUseCase;
    private final ActualizarClienteUseCase actualizarClienteUseCase;
    private final ObtenerClienteUseCase obtenerClienteUseCase;
    private final ListarClientesUseCase listarClientesUseCase;

    public ClienteController(CrearClienteUseCase crearClienteUseCase,
                             ActualizarClienteUseCase actualizarClienteUseCase,
                             ObtenerClienteUseCase obtenerClienteUseCase,
                             ListarClientesUseCase listarClientesUseCase) {
        this.crearClienteUseCase = crearClienteUseCase;
        this.actualizarClienteUseCase = actualizarClienteUseCase;
        this.obtenerClienteUseCase = obtenerClienteUseCase;
        this.listarClientesUseCase = listarClientesUseCase;
    }

    @PostMapping
    public ResponseEntity<ClienteDto> crear(@RequestBody CrearClienteRequest request) {
        Cliente cliente = crearClienteUseCase.ejecutar(new CrearClienteCommand(
                request.nombre(), request.apellido(), request.dni(), request.email(), request.telefono()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(cliente.getId())
                .toUri();
        return ResponseEntity.created(location).body(ClienteDto.from(cliente));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDto> obtener(@PathVariable Long id) {
        AuthenticatedUser usuario = usuarioAutenticado();
        Cliente cliente = obtenerClienteUseCase.ejecutar(
                new ObtenerClienteQuery(id, usuario.rol(), usuario.clienteId()));
        return ResponseEntity.ok(ClienteDto.from(cliente));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteDto> actualizar(@PathVariable Long id,
                                                 @RequestBody ActualizarClienteRequest request) {
        Cliente cliente = actualizarClienteUseCase.ejecutar(new ActualizarClienteCommand(
                id, request.nombre(), request.apellido(), request.dni(), request.email(), request.telefono()));
        return ResponseEntity.ok(ClienteDto.from(cliente));
    }

    @GetMapping
    public ResponseEntity<List<ClienteDto>> listar() {
        List<ClienteDto> clientes = listarClientesUseCase.ejecutar(new ListarClientesQuery())
                .stream()
                .map(ClienteDto::from)
                .toList();
        return ResponseEntity.ok(clientes);
    }

    private AuthenticatedUser usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
