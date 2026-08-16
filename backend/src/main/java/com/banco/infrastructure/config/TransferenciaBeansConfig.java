package com.banco.infrastructure.config;

import com.banco.application.usecase.ObtenerMovimientosUseCase;
import com.banco.application.usecase.TransferirUseCase;
import com.banco.application.validator.TransferValidator;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.port.TransferenciaEventPublisher;
import com.banco.domain.vo.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Wiring de transferencias (SPEC-004). Mantiene el paquete application libre
 * de Spring (regla ArchUnit): los use cases reciben puertos de dominio y el
 * {@link TransferValidator} recibe el límite diario como {@link Money}
 * (la capa de aplicación no lee propiedades de Spring — A-002, §8.7).
 *
 * {@code TransferenciaEventPublisher} se resuelve al único {@code @Component}
 * que lo implementa ({@code TransferenciaEventPublisherNoop}).
 * {@code TransferenciaService} no necesita declaración (component scanning).
 */
@Configuration
public class TransferenciaBeansConfig {

    @Bean
    public Money limiteDiarioTransferencias(
            @Value("${banco.negocio.limite-diario-transferencias:200000}") BigDecimal limite) {
        return Money.ars(limite);
    }

    @Bean
    public TransferValidator transferValidator(CuentaRepository cuentaRepository,
                                               Money limiteDiarioTransferencias) {
        return new TransferValidator(cuentaRepository, limiteDiarioTransferencias);
    }

    @Bean
    public TransferirUseCase transferirUseCase(CuentaRepository cuentaRepository,
                                               MovimientoRepository movimientoRepository,
                                               TransferValidator transferValidator,
                                               TransferenciaEventPublisher transferenciaEventPublisher) {
        return new TransferirUseCase(cuentaRepository, movimientoRepository,
                transferValidator, transferenciaEventPublisher);
    }

    @Bean
    public ObtenerMovimientosUseCase obtenerMovimientosUseCase(CuentaRepository cuentaRepository,
                                                               MovimientoRepository movimientoRepository) {
        return new ObtenerMovimientosUseCase(cuentaRepository, movimientoRepository);
    }
}
