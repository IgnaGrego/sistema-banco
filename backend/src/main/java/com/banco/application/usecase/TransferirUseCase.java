package com.banco.application.usecase;

import com.banco.application.command.TransferirCommand;
import com.banco.application.validator.DatosTransferencia;
import com.banco.application.validator.TransferValidator;
import com.banco.application.validator.TransferenciaValidada;
import com.banco.domain.event.TransferenciaRealizada;
import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.port.TransferenciaEventPublisher;

import java.time.Instant;

/**
 * Orquesta el main flow de la transferencia (spec §6): valida, debita el
 * origen, acredita el destino, persiste las dos cuentas y los dos movimientos
 * y emite {@link TransferenciaRealizada} (FR-004). Corre dentro de la única
 * transacción de BD abierta por {@code TransferenciaService} (FR-002 —
 * ADR-006); si cualquier paso falla, rollback total.
 *
 * {@code idTransferencia} de la confirmación = id del Movimiento
 * TRANSFERENCIA_SALIENTE (A-007).
 */
public class TransferirUseCase {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final TransferValidator transferValidator;
    private final TransferenciaEventPublisher eventPublisher;

    public TransferirUseCase(CuentaRepository cuentaRepository,
                             MovimientoRepository movimientoRepository,
                             TransferValidator transferValidator,
                             TransferenciaEventPublisher eventPublisher) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.transferValidator = transferValidator;
        this.eventPublisher = eventPublisher;
    }

    public TransferenciaConfirmacion ejecutar(TransferirCommand command, Long clienteIdAutenticado) {
        DatosTransferencia datos = new DatosTransferencia(
                command.cuentaOrigenId(), command.cbuDestino(), command.monto(), clienteIdAutenticado);

        TransferenciaValidada validada = transferValidator.validar(datos);

        // Una sola fecha compartida por ambos movimientos (FR-003).
        Instant fecha = Instant.now();

        var origen = validada.origen();
        var destino = validada.destino();
        var monto = validada.monto();

        origen.debitar(monto);   // BR-001 (doble barrera con el paso 9 del validador)
        destino.acreditar(monto);

        cuentaRepository.save(origen);
        cuentaRepository.save(destino);

        // FR-003: exactamente dos movimientos, mismo monto y fecha, contrapartes cruzadas.
        Movimiento saliente = movimientoRepository.save(Movimiento.crear(
                origen.getId(), TipoMovimiento.TRANSFERENCIA_SALIENTE, monto, fecha, destino.getId()));
        Movimiento entrante = movimientoRepository.save(Movimiento.crear(
                destino.getId(), TipoMovimiento.TRANSFERENCIA_ENTRANTE, monto, fecha, origen.getId()));

        // FR-004: evento de dominio (sin suscriptores en este sprint).
        eventPublisher.publicar(new TransferenciaRealizada(
                monto, origen.getCbu(), destino.getCbu(), fecha, saliente.getId()));

        // FR-001, A-007: idTransferencia = id del Movimiento saliente.
        return new TransferenciaConfirmacion(saliente.getId(), monto.monto(),
                destino.getCbu().valor(), fecha);
    }
}
