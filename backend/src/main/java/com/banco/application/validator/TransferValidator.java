package com.banco.application.validator;

import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.AutoTransferenciaException;
import com.banco.domain.exception.CbuInvalidoException;
import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.LimiteDiarioExcedidoException;
import com.banco.domain.exception.MonedaIncompatibleException;
import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Money;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Validación de la transferencia (BR-002..BR-007, §9 de la spec): clase única
 * con los 10 chequeos SECUENCIALES en el orden exacto del main flow (spec §6
 * paso 2) que corta ante el primer error (docs/architecture/SPEC-004.md §8.4).
 * Devuelve {@link TransferenciaValidada} con las cuentas ya cargadas y el
 * monto validado.
 *
 * {@code limiteDiario} es un {@link Money} inyectado por constructor (bean de
 * configuración — la capa de aplicación no lee propiedades de Spring).
 */
public class TransferValidator {

    private final CuentaRepository cuentaRepository;
    private final Money limiteDiario;

    public TransferValidator(CuentaRepository cuentaRepository, Money limiteDiario) {
        this.cuentaRepository = cuentaRepository;
        this.limiteDiario = limiteDiario;
    }

    public TransferenciaValidada validar(DatosTransferencia datos) {
        // 1. La cuenta origen existe (ERR-002 → 404).
        Cuenta origen = cuentaRepository.findById(datos.cuentaOrigenId())
                .orElseThrow(CuentaNoEncontradaException::new);

        // 2. La cuenta origen pertenece al cliente autenticado (ERR-006 → 403).
        if (datos.clienteIdClaim() == null || !datos.clienteIdClaim().equals(origen.getClienteId())) {
            throw new AccesoDenegadoException();
        }

        // 3. La cuenta origen está ACTIVA (ERR-003 → 422, BR-002).
        if (origen.getEstado() != EstadoCuenta.ACTIVA) {
            throw new CuentaBloqueadaException();
        }

        // 4. CBU destino válido (formato → 400 con campo cbuDestino) y existe
        //    (ERR-002 → 404, AF-001). El VO CBU valida en su constructor.
        CBU cbuDestino;
        try {
            cbuDestino = new CBU(datos.cbuDestino());
        } catch (CbuInvalidoException e) {
            throw new DatosInvalidosException("cbuDestino", e.getMessage());
        }
        Cuenta destino = cuentaRepository.findByCbu(cbuDestino)
                .orElseThrow(CuentaNoEncontradaException::new);

        // 5. El destino es distinto del origen (ERR-008 → 422, BR-005).
        if (destino.getId().equals(origen.getId())) {
            throw new AutoTransferenciaException();
        }

        // 6. La cuenta destino está ACTIVA (ERR-003 → 422, BR-002).
        if (destino.getEstado() != EstadoCuenta.ACTIVA) {
            throw new CuentaBloqueadaException();
        }

        // 7. Monto válido: > 0, hasta 2 decimales (ERR-004 → 400, BR-003).
        //    El pre-chequeo ocurre ANTES de construir el Money: el VO (SPEC-002)
        //    solo valida monto ≥ 0 (MoneyInvalidoException no está mapeada →
        //    caería en 500); el signo/escala (BR-003) se exige aquí para que un
        //    monto inválido responda 400 DATOS_INVALIDOS con el campo "monto".
        if (datos.monto() == null || datos.monto().signum() <= 0 || datos.monto().scale() > 2) {
            throw new DatosInvalidosException("monto",
                    "El monto debe ser mayor a 0 y tener hasta 2 decimales");
        }
        Money monto = Money.ars(datos.monto());

        // 8. Monedas compatibles (ERR-009 → 422, BR-007). Se compara la
        //    igualdad de Money.moneda() (código ISO 4217 alpha-3 del record
        //    Moneda de SPEC-002 — ADR-007).
        if (!origen.getSaldo().moneda().equals(destino.getSaldo().moneda())) {
            throw new MonedaIncompatibleException();
        }

        // 9. Saldo suficiente en origen (ERR-001 → 422, BR-001). Doble barrera:
        //    debitar re-verifica el invariante del agregado.
        if (monto.esMayorQue(origen.getSaldo())) {
            throw new SaldoInsuficienteException();
        }

        // 10. Límite diario del cliente no superado (ERR-007 → 422, BR-004,
        //     AF-002). "Día calendario" en UTC (convención de almacenamiento
        //     del repo — V1). Condición "alcanza o supera" (>=).
        Money totalDia = cuentaRepository.montoTotalTransferenciasSalientesDelDia(
                datos.clienteIdClaim(), LocalDate.now(ZoneOffset.UTC));
        if (totalDia.sumar(monto).esMayorOIgualQue(limiteDiario)) {
            throw new LimiteDiarioExcedidoException();
        }

        return new TransferenciaValidada(origen, destino, monto);
    }
}
