package com.banco.application.usecase;

import com.banco.application.command.ActualizarClienteCommand;
import com.banco.application.validator.ClienteValidator;
import com.banco.application.validator.DatosCliente;
import com.banco.domain.exception.ClienteDuplicadoException;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;

/**
 * Edición de cliente (FR-003, PUT total A-004): mismas reglas que el alta; la
 * unicidad se evalúa excluyendo al propio cliente (BR-001, BR-003).
 */
public class ActualizarClienteUseCase {

    private final ClienteRepository repository;
    private final ClienteValidator validator;

    public ActualizarClienteUseCase(ClienteRepository repository, ClienteValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public Cliente ejecutar(ActualizarClienteCommand command) {
        Cliente cliente = repository.findById(command.id())
                .orElseThrow(ClienteNoEncontradoException::new);

        DatosCliente datos = new DatosCliente(
                command.nombre(), command.apellido(), command.dni(), command.email(), command.telefono());

        validator.validar(datos);

        DNI dni = new DNI(datos.dni());
        if (repository.existsByDniAndIdNot(dni, command.id())) {
            throw new ClienteDuplicadoException("dni", dni.valor());
        }
        if (repository.existsByEmailAndIdNot(datos.email(), command.id())) {
            throw new ClienteDuplicadoException("email", datos.email());
        }

        cliente.actualizar(datos.nombre(), datos.apellido(), dni, datos.email(), datos.telefono());
        return repository.save(cliente);
    }
}
