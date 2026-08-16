package com.banco.infrastructure.config;

import com.banco.application.usecase.RealizarDepositoUseCase;
import com.banco.application.usecase.RealizarRetiroUseCase;
import com.banco.application.validator.DepositoRetiroValidator;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.DepositoEventPublisher;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.port.RetiroEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de depósitos y retiros (SPEC-005). Mantiene el paquete application
 * libre de Spring (regla ArchUnit — AC-020): los use cases y el validador
 * reciben puertos de dominio. Sin {@code @Value}: SPEC-005 no introduce
 * propiedades nuevas (sin límites ni topes — spec §12).
 *
 * <p>{@code DepositoEventPublisher}/{@code RetiroEventPublisher} se resuelven
 * a los únicos {@code @Component} que los implementan
 * ({@code DepositoEventPublisherNoop}/{@code RetiroEventPublisherNoop}).
 * {@code DepositoService}/{@code RetiroService} no necesitan declaración
 * (component scanning).
 */
@Configuration
public class DepositoRetiroBeansConfig {

    @Bean
    public DepositoRetiroValidator depositoRetiroValidator(CuentaRepository cuentaRepository) {
        return new DepositoRetiroValidator(cuentaRepository);
    }

    @Bean
    public RealizarDepositoUseCase realizarDepositoUseCase(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            DepositoRetiroValidator depositoRetiroValidator,
            DepositoEventPublisher depositoEventPublisher) {
        return new RealizarDepositoUseCase(cuentaRepository, movimientoRepository,
                depositoRetiroValidator, depositoEventPublisher);
    }

    @Bean
    public RealizarRetiroUseCase realizarRetiroUseCase(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            DepositoRetiroValidator depositoRetiroValidator,
            RetiroEventPublisher retiroEventPublisher) {
        return new RealizarRetiroUseCase(cuentaRepository, movimientoRepository,
                depositoRetiroValidator, retiroEventPublisher);
    }
}
