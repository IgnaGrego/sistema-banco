package com.banco.infrastructure.config;

import com.banco.application.usecase.AbrirCuentaUseCase;
import com.banco.application.usecase.ListarCuentasUseCase;
import com.banco.application.usecase.ObtenerCuentaPorCbuUseCase;
import com.banco.application.usecase.ObtenerCuentaUseCase;
import com.banco.application.validator.AperturaValidator;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.CuentaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de los use cases de cuentas y del validador de apertura. Mantiene el
 * paquete application libre de Spring (regla ArchUnit).
 */
@Configuration
public class CuentaBeansConfig {

    @Bean
    public AperturaValidator aperturaValidator() {
        return new AperturaValidator();
    }

    @Bean
    public AbrirCuentaUseCase abrirCuentaUseCase(CuentaRepository cuentaRepository,
                                                 ClienteRepository clienteRepository,
                                                 AperturaValidator aperturaValidator) {
        return new AbrirCuentaUseCase(cuentaRepository, clienteRepository, aperturaValidator);
    }

    @Bean
    public ObtenerCuentaUseCase obtenerCuentaUseCase(CuentaRepository cuentaRepository) {
        return new ObtenerCuentaUseCase(cuentaRepository);
    }

    @Bean
    public ObtenerCuentaPorCbuUseCase obtenerCuentaPorCbuUseCase(CuentaRepository cuentaRepository) {
        return new ObtenerCuentaPorCbuUseCase(cuentaRepository);
    }

    @Bean
    public ListarCuentasUseCase listarCuentasUseCase(CuentaRepository cuentaRepository,
                                                     ClienteRepository clienteRepository) {
        return new ListarCuentasUseCase(cuentaRepository, clienteRepository);
    }
}
