package com.banco.infrastructure.service;

import com.banco.application.command.TransferirCommand;
import com.banco.application.usecase.TransferenciaConfirmacion;
import com.banco.application.usecase.TransferirUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontera transaccional de la transferencia (FR-002 — ADR-006): el ÚNICO
 * {@code @Transactional} del sistema. El use case (Java puro, sin Spring)
 * corre dentro de esta transacción: las lecturas de validación y las 4
 * escrituras (origen, destino, saliente, entrante) comparten el mismo tx de
 * BD; cualquier fallo → rollback total.
 *
 * Ante un conflicto de {@code @Version} el commit lanza
 * {@code ObjectOptimisticLockingFailureException} → 409 CONFLICTO_CONCURRENCIA
 * por el handler (BR-006, A-004; sin reintento automático).
 */
@Service
public class TransferenciaService {

    private final TransferirUseCase transferirUseCase;

    public TransferenciaService(TransferirUseCase transferirUseCase) {
        this.transferirUseCase = transferirUseCase;
    }

    @Transactional
    public TransferenciaConfirmacion ejecutar(TransferirCommand command, Long clienteIdAutenticado) {
        return transferirUseCase.ejecutar(command, clienteIdAutenticado);
    }
}
