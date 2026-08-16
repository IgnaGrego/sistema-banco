package com.banco.infrastructure.config;

import com.banco.application.usecase.ActualizarClienteUseCase;
import com.banco.application.usecase.CrearClienteUseCase;
import com.banco.application.usecase.ListarClientesUseCase;
import com.banco.application.usecase.ObtenerClienteUseCase;
import com.banco.application.validator.CamposObligatoriosValidador;
import com.banco.application.validator.ClienteValidator;
import com.banco.application.validator.FormatoValidador;
import com.banco.application.validator.LongitudValidador;
import com.banco.domain.port.ClienteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wiring de los use cases y de la cadena de validación (CoR) en el orden:
 * obligatorios → longitudes → formatos. Mantiene el paquete application libre
 * de Spring (regla ArchUnit).
 */
@Configuration
public class ClienteBeansConfig {

    @Bean
    public ClienteValidator clienteValidator() {
        return new ClienteValidator(List.of(
                new CamposObligatoriosValidador(),
                new LongitudValidador(),
                new FormatoValidador()));
    }

    @Bean
    public CrearClienteUseCase crearClienteUseCase(ClienteRepository clienteRepository,
                                                   ClienteValidator clienteValidator) {
        return new CrearClienteUseCase(clienteRepository, clienteValidator);
    }

    @Bean
    public ActualizarClienteUseCase actualizarClienteUseCase(ClienteRepository clienteRepository,
                                                             ClienteValidator clienteValidator) {
        return new ActualizarClienteUseCase(clienteRepository, clienteValidator);
    }

    @Bean
    public ObtenerClienteUseCase obtenerClienteUseCase(ClienteRepository clienteRepository) {
        return new ObtenerClienteUseCase(clienteRepository);
    }

    @Bean
    public ListarClientesUseCase listarClientesUseCase(ClienteRepository clienteRepository) {
        return new ListarClientesUseCase(clienteRepository);
    }
}
