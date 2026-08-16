package com.banco.application.usecase;

import com.banco.application.command.RealizarDepositoCommand;
import com.banco.application.validator.DatosDepositoRetiro;
import com.banco.application.validator.DepositoRetiroValidado;
import com.banco.application.validator.DepositoRetiroValidator;
import com.banco.domain.event.DepositoRealizado;
import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.DepositoEventPublisher;
import com.banco.domain.port.MovimientoRepository;

import java.time.Instant;

/**
 * Orquesta el main flow del depósito (spec §6, FR-001): valida, acredita el
 * saldo, persiste la cuenta y el {@code Movimiento} DEPOSITO y emite
 * {@link DepositoRealizado} (FR-004). Corre dentro de la única transacción de
 * BD abierta por {@code DepositoService} (BR-004 — ADR-006); si cualquier paso
 * falla, rollback total.
 */
public class RealizarDepositoUseCase {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final DepositoRetiroValidator depositoRetiroValidator;
    private final DepositoEventPublisher depositoEventPublisher;

    public RealizarDepositoUseCase(CuentaRepository cuentaRepository,
                                   MovimientoRepository movimientoRepository,
                                   DepositoRetiroValidator depositoRetiroValidator,
                                   DepositoEventPublisher depositoEventPublisher) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.depositoRetiroValidator = depositoRetiroValidator;
        this.depositoEventPublisher = depositoEventPublisher;
    }

    public DepositoConfirmacion ejecutar(RealizarDepositoCommand command) {
        DatosDepositoRetiro datos = new DatosDepositoRetiro(
                command.cuentaId(), command.monto(), command.rol(), command.clienteIdClaim());

        DepositoRetiroValidado validada = depositoRetiroValidator.validarDeposito(datos);

        Instant fecha = Instant.now();

        var cuenta = validada.cuenta();
        var monto = validada.monto();

        cuenta.acreditar(monto);   // BR-002 (guarda verificarActiva: doble barrera)

        // BR-004: UPDATE ... WHERE id = ? AND version = N (optimistic lock).
        cuentaRepository.save(cuenta);

        // FR-003: exactamente un movimiento DEPOSITO, sin contraparte (null).
        Movimiento movimiento = movimientoRepository.save(Movimiento.crear(
                cuenta.getId(), TipoMovimiento.DEPOSITO, monto, fecha, null));

        // FR-004: evento de dominio (sin suscriptores en este sprint).
        depositoEventPublisher.publicar(new DepositoRealizado(
                monto, cuenta.getId(), fecha, movimiento.getId()));

        // FR-001, A-002: confirmación = contrato de la respuesta 201.
        return new DepositoConfirmacion(movimiento.getId(), cuenta.getId(),
                monto.monto(), fecha);
    }
}
