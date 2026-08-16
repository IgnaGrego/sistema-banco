package com.banco.application.usecase;

import com.banco.application.command.RealizarRetiroCommand;
import com.banco.application.validator.DatosDepositoRetiro;
import com.banco.application.validator.DepositoRetiroValidado;
import com.banco.application.validator.DepositoRetiroValidator;
import com.banco.domain.event.RetiroRealizado;
import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.port.RetiroEventPublisher;

import java.time.Instant;

/**
 * Orquesta el main flow del retiro (spec §6, FR-002): valida, debita el
 * saldo, persiste la cuenta y el {@code Movimiento} RETIRO y emite
 * {@link RetiroRealizado} (FR-004). Corre dentro de la única transacción de
 * BD abierta por {@code RetiroService} (BR-004 — ADR-006); si cualquier paso
 * falla, rollback total.
 */
public class RealizarRetiroUseCase {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final DepositoRetiroValidator depositoRetiroValidator;
    private final RetiroEventPublisher retiroEventPublisher;

    public RealizarRetiroUseCase(CuentaRepository cuentaRepository,
                                 MovimientoRepository movimientoRepository,
                                 DepositoRetiroValidator depositoRetiroValidator,
                                 RetiroEventPublisher retiroEventPublisher) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.depositoRetiroValidator = depositoRetiroValidator;
        this.retiroEventPublisher = retiroEventPublisher;
    }

    public RetiroConfirmacion ejecutar(RealizarRetiroCommand command) {
        DatosDepositoRetiro datos = new DatosDepositoRetiro(
                command.cuentaId(), command.monto(), command.rol(), command.clienteIdClaim());

        DepositoRetiroValidado validada = depositoRetiroValidator.validarRetiro(datos);

        Instant fecha = Instant.now();

        var cuenta = validada.cuenta();
        var monto = validada.monto();

        cuenta.debitar(monto);   // BR-002 (invariante saldo nunca negativo — doble barrera)

        // BR-004: UPDATE ... WHERE id = ? AND version = N (optimistic lock).
        cuentaRepository.save(cuenta);

        // FR-003: exactamente un movimiento RETIRO, sin contraparte (null).
        Movimiento movimiento = movimientoRepository.save(Movimiento.crear(
                cuenta.getId(), TipoMovimiento.RETIRO, monto, fecha, null));

        // FR-004: evento de dominio (sin suscriptores en este sprint).
        retiroEventPublisher.publicar(new RetiroRealizado(
                monto, cuenta.getId(), fecha, movimiento.getId()));

        // FR-002, A-002: confirmación = contrato de la respuesta 201.
        return new RetiroConfirmacion(movimiento.getId(), cuenta.getId(),
                monto.monto(), fecha);
    }
}
