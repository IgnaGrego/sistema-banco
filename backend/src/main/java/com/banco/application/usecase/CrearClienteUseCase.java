package com.banco.application.usecase;

import com.banco.application.command.CrearClienteCommand;
import com.banco.application.validator.ClienteValidator;
import com.banco.application.validator.DatosCliente;
import com.banco.domain.exception.ClienteDuplicadoException;
import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;

import java.time.Instant;

/**
 * Alta de cliente (FR-001): normaliza, valida (CoR), verifica unicidad y
 * persiste con fechaAlta automática (FR-005).
 */
public class CrearClienteUseCase {

    private final ClienteRepository repository;
    private final ClienteValidator validator;

    public CrearClienteUseCase(ClienteRepository repository, ClienteValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public Cliente ejecutar(CrearClienteCommand command) {
        DatosCliente datos = new DatosCliente(
                command.nombre(), command.apellido(), command.dni(), command.email(), command.telefono());

        validator.validar(datos);

        DNI dni = new DNI(datos.dni()); // garantizado válido por la CoR
        if (repository.existsByDni(dni)) {
            throw new ClienteDuplicadoException("dni", dni.valor());
        }
        if (repository.existsByEmail(datos.email())) {
            throw new ClienteDuplicadoException("email", datos.email());
        }

        Cliente cliente = Cliente.crear(datos.nombre(), datos.apellido(), dni,
                datos.email(), datos.telefono(), Instant.now());
        return repository.save(cliente);
    }
}
