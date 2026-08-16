package com.banco.application.validator;

import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.Money;

import java.math.BigDecimal;

/**
 * Validación del depósito y del retiro (BR-001..BR-003, §9 de la spec): clase
 * única con dos cadenas públicas de chequeos SECUENCIALES en el orden exacto
 * del main flow (spec §6 paso 2) que cortan ante el primer error
 * (docs/architecture/SPEC-005.md §8.4). Las cadenas comparten 3 chequeos
 * (existe, ACTIVA, monto) que viven como helpers privados; las diferencias
 * (rol en depósito; propiedad y saldo en retiro) quedan explícitas en cada
 * método público. Devuelve {@link DepositoRetiroValidado} con la cuenta ya
 * cargada y el monto validado.
 */
public class DepositoRetiroValidator {

    private static final String MENSAJE_MONTO_INVALIDO =
            "El monto debe ser mayor a 0 y tener hasta 2 decimales";

    private final CuentaRepository cuentaRepository;

    public DepositoRetiroValidator(CuentaRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    /**
     * Cadena de validación del depósito (5 chequeos, §8.4): solo {@code ADMIN}
     * deposita (A-001, ERR-005 → 403) → la cuenta existe (ERR-003 → 404) → la
     * cuenta está ACTIVA (ERR-004 → 422, BR-003) → monto válido (ERR-001 →
     * 400, BR-001). Defensa en profundidad: el matcher {@code hasRole("ADMIN")}
     * ya produce el 403 HTTP; este chequeo habilita la cobertura unitaria de
     * ERR-005 (AC-018).
     */
    public DepositoRetiroValidado validarDeposito(DatosDepositoRetiro datos) {
        // 1. Solo ADMIN deposita (ERR-005 → 403, A-001). Chequeo de claim puro:
        //    corre primero — un CLIENTE obtiene 403 antes de cualquier consulta.
        if (!"ADMIN".equals(datos.rol())) {
            throw new AccesoDenegadoException();
        }

        Cuenta cuenta = cargarCuenta(datos.cuentaId());
        verificarActiva(cuenta);
        Money monto = validarMonto(datos.monto());

        return new DepositoRetiroValidado(cuenta, monto);
    }

    /**
     * Cadena de validación del retiro (6 chequeos, §8.4): la cuenta existe
     * (404-antes-403) → propiedad para {@code CLIENTE} (ERR-005 → 403, §9) →
     * la cuenta está ACTIVA (ERR-004 → 422, BR-003) → monto válido (ERR-001 →
     * 400, BR-001) → saldo suficiente (ERR-002 → 422, BR-002). El orden
     * 404-antes-403 es deliberado: la propiedad exige la cuenta cargada (misma
     * racional que {@code ObtenerMovimientosUseCase} — SPEC-004 §8.3).
     */
    public DepositoRetiroValidado validarRetiro(DatosDepositoRetiro datos) {
        // 1. La cuenta existe (ERR-003 → 404).
        Cuenta cuenta = cargarCuenta(datos.cuentaId());

        // 2. Un CLIENTE solo retira de cuentas propias (ERR-005 → 403, §9;
        //    réplica exacta del chequeo de ObtenerMovimientosUseCase). Un
        //    CLIENTE con clienteIdClaim null (token inconsistente) recibe 403.
        if ("CLIENTE".equals(datos.rol())
                && (datos.clienteIdClaim() == null
                || !datos.clienteIdClaim().equals(cuenta.getClienteId()))) {
            throw new AccesoDenegadoException();
        }

        verificarActiva(cuenta);
        Money monto = validarMonto(datos.monto());

        // 5. Saldo suficiente (ERR-002 → 422, BR-002). Doble barrera: debitar
        //    re-verifica el invariante del agregado.
        if (monto.esMayorQue(cuenta.getSaldo())) {
            throw new SaldoInsuficienteException();
        }

        return new DepositoRetiroValidado(cuenta, monto);
    }

    private Cuenta cargarCuenta(Long cuentaId) {
        return cuentaRepository.findById(cuentaId)
                .orElseThrow(CuentaNoEncontradaException::new);
    }

    private void verificarActiva(Cuenta cuenta) {
        if (cuenta.getEstado() != EstadoCuenta.ACTIVA) {
            throw new CuentaBloqueadaException();
        }
    }

    /**
     * Monto válido: > 0, hasta 2 decimales (ERR-001 → 400, BR-001). El
     * pre-chequeo de signo/escala ocurre ANTES de construir el {@link Money}:
     * el VO (SPEC-002) solo valida monto ≥ 0 ({@code MoneyInvalidoException} no
     * está mapeada → caería en 500); el chequeo explícito garantiza 400
     * DATOS_INVALIDOS con el campo "monto" (mismo razonamiento que el paso 7 de
     * {@code TransferValidator}).
     */
    private Money validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2) {
            throw new DatosInvalidosException("monto", MENSAJE_MONTO_INVALIDO);
        }
        return Money.ars(monto);
    }
}
