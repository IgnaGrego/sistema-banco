package com.banco.infrastructure.service;

import com.banco.application.command.RealizarRetiroCommand;
import com.banco.application.usecase.RealizarRetiroUseCase;
import com.banco.application.usecase.RetiroConfirmacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontera transaccional del retiro (BR-004 — ADR-006, decisión 2 extendida a
 * SPEC-005): el use case (Java puro, sin Spring) corre dentro de esta
 * transacción; las lecturas de validación y las 2 escrituras
 * ({@code save(cuenta)}, {@code save(movimiento)}) comparten el mismo tx de
 * BD; cualquier fallo → rollback total.
 *
 * <p>Ante un conflicto de {@code @Version} el commit lanza
 * {@code ObjectOptimisticLockingFailureException} → 409 CONFLICTO_CONCURRENCIA
 * por el handler (BR-004, ERR-006; sin reintento automático).
 */
@Service
public class RetiroService {

    private final RealizarRetiroUseCase realizarRetiroUseCase;

    public RetiroService(RealizarRetiroUseCase realizarRetiroUseCase) {
        this.realizarRetiroUseCase = realizarRetiroUseCase;
    }

    @Transactional
    public RetiroConfirmacion ejecutar(RealizarRetiroCommand command) {
        return realizarRetiroUseCase.ejecutar(command);
    }
}
