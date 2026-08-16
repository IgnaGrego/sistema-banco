package com.banco.application;

import com.banco.application.validator.DatosDepositoRetiro;
import com.banco.application.validator.DepositoRetiroValidado;
import com.banco.application.validator.DepositoRetiroValidator;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.SaldoInsuficienteException;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cadenas de validación del depósito y del retiro (AC-003..AC-006,
 * AC-010..AC-014 lógica, AC-018): un caso por chequeo, en el ORDEN exacto del
 * main flow (spec §6 paso 2, docs/architecture/SPEC-005.md §8.4). La cadena
 * corta ante el primer error.
 */
class DepositoRetiroValidatorTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");
    private static final Moneda ARS = new Moneda("ARS");

    private CuentaRepository cuentaRepository;
    private DepositoRetiroValidator validator;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaRepository.class);
        validator = new DepositoRetiroValidator(cuentaRepository);
    }

    // --- validarDeposito ---

    // Chequeo 1: solo ADMIN deposita (ERR-005 → 403, A-001)

    @Test
    void depositoConRolClienteLanzaAccesoDenegado() {
        // El rol corre primero: un CLIENTE obtiene 403 antes de cualquier
        // consulta a BD (defensa en profundidad — §8.5).
        assertThrows(AccesoDenegadoException.class,
                () -> validator.validarDeposito(datos(10L, "100.00", "CLIENTE", 7L)));
    }

    @Test
    void depositoConRolNuloLanzaAccesoDenegado() {
        assertThrows(AccesoDenegadoException.class,
                () -> validator.validarDeposito(datos(10L, "100.00", null, null)));
    }

    // Chequeo 2: la cuenta existe (ERR-003 → 404)

    @Test
    void depositoEnCuentaInexistenteLanzaCuentaNoEncontrada() {
        when(cuentaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> validator.validarDeposito(datos(99L, "100.00", "ADMIN", null)));
    }

    // Chequeo 3: la cuenta está ACTIVA (ERR-004 → 422, BR-003)

    @Test
    void depositoEnCuentaBloqueadaLanzaCuentaBloqueada() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuenta(10L, 7L, EstadoCuenta.BLOQUEADA)));

        assertThrows(CuentaBloqueadaException.class,
                () -> validator.validarDeposito(datos(10L, "100.00", "ADMIN", null)));
    }

    // Chequeo 4: monto > 0, hasta 2 decimales (ERR-001 → 400, BR-001)

    @Test
    void depositoMontoNuloLanzaDatosInvalidosConCampoMonto() {
        stubCuentaActiva();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validarDeposito(datos(10L, null, "ADMIN", null)));

        assertEquals("monto", ex.getCampo());
    }

    @Test
    void depositoMontoCeroLanzaDatosInvalidosConCampoMonto() {
        stubCuentaActiva();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validarDeposito(datos(10L, "0", "ADMIN", null)));

        assertEquals("monto", ex.getCampo());
    }

    @Test
    void depositoMontoNegativoLanzaDatosInvalidosConCampoMonto() {
        stubCuentaActiva();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validarDeposito(datos(10L, "-5.00", "ADMIN", null)));

        assertEquals("monto", ex.getCampo());
    }

    @Test
    void depositoMontoConMasDe2DecimalesLanzaDatosInvalidosConCampoMonto() {
        stubCuentaActiva();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validarDeposito(datos(10L, "100.123", "ADMIN", null)));

        assertEquals("monto", ex.getCampo());
    }

    // Chequeo 5: happy path → cuenta cargada + Money ARS

    @Test
    void depositoHappyPathDevuelveCuentaCargadaYMontoValidado() {
        stubCuentaActiva();

        DepositoRetiroValidado validada = validator.validarDeposito(datos(10L, "15000.00", "ADMIN", null));

        assertEquals(10L, validada.cuenta().getId());
        assertEquals(new BigDecimal("15000.00"), validada.monto().monto());
        assertEquals("ARS", validada.monto().moneda().codigo());
    }

    // --- validarRetiro ---

    // Chequeo 1: la cuenta existe (ERR-003 → 404; 404-antes-403)

    @Test
    void retiroEnCuentaInexistenteLanzaCuentaNoEncontrada() {
        when(cuentaRepository.findById(99L)).thenReturn(Optional.empty());

        // Incluso un CLIENTE con claim: la existencia (404) se verifica antes
        // de la propiedad (403) — orden 404-antes-403 (§8.4).
        assertThrows(CuentaNoEncontradaException.class,
                () -> validator.validarRetiro(datos(99L, "100.00", "CLIENTE", 7L)));
    }

    // Chequeo 2: un CLIENTE solo retira de cuentas propias (ERR-005 → 403)

    @Test
    void retiroDeCuentaAjenaConClaimDistintoLanzaAccesoDenegado() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuenta(10L, 7L, EstadoCuenta.ACTIVA)));

        assertThrows(AccesoDenegadoException.class,
                () -> validator.validarRetiro(datos(10L, "100.00", "CLIENTE", 999L)));
    }

    @Test
    void retiroDeClienteSinClienteIdClaimLanzaAccesoDenegado() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuenta(10L, 7L, EstadoCuenta.ACTIVA)));

        assertThrows(AccesoDenegadoException.class,
                () -> validator.validarRetiro(datos(10L, "100.00", "CLIENTE", null)));
    }

    @Test
    void retiroAdminSobreCuentaDeOtroClientePasa() {
        // AC-017: el ADMIN no tiene chequeo de propiedad — retira de cualquier
        // cuenta (con saldo suficiente).
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuenta(10L, 7L, EstadoCuenta.ACTIVA)));

        DepositoRetiroValidado validada = validator.validarRetiro(datos(10L, "100.00", "ADMIN", null));

        assertEquals(10L, validada.cuenta().getId());
    }

    // Chequeo 3: la cuenta está ACTIVA (ERR-004 → 422, BR-003)

    @Test
    void retiroEnCuentaBloqueadaLanzaCuentaBloqueada() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuenta(10L, 7L, EstadoCuenta.BLOQUEADA)));

        assertThrows(CuentaBloqueadaException.class,
                () -> validator.validarRetiro(datos(10L, "100.00", "ADMIN", null)));
    }

    // Chequeo 4: monto > 0, hasta 2 decimales (ERR-001 → 400, BR-001)

    @Test
    void retiroMontoInvalidoLanzaDatosInvalidosConCampoMonto() {
        stubCuentaActiva();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validarRetiro(datos(10L, "0", "ADMIN", null)));

        assertEquals("monto", ex.getCampo());
    }

    // Chequeo 5: saldo suficiente (ERR-002 → 422, BR-002)

    @Test
    void retiroSaldoInsuficienteLanzaSaldoInsuficiente() {
        // Saldo 100 < monto 150.
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(
                cuenta(10L, 7L, EstadoCuenta.ACTIVA, new BigDecimal("100.00"))));

        assertThrows(SaldoInsuficienteException.class,
                () -> validator.validarRetiro(datos(10L, "150.00", "ADMIN", null)));
    }

    // Chequeo 6: happy path → cuenta cargada + Money ARS

    @Test
    void retiroHappyPathDevuelveCuentaCargadaYMontoValidado() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(
                cuenta(10L, 7L, EstadoCuenta.ACTIVA, new BigDecimal("100000.00"))));

        DepositoRetiroValidado validada = validator.validarRetiro(datos(10L, "60000.00", "CLIENTE", 7L));

        assertEquals(10L, validada.cuenta().getId());
        assertEquals(new BigDecimal("60000.00"), validada.monto().monto());
        assertEquals("ARS", validada.monto().moneda().codigo());
    }

    // --- Helpers ---

    private static DatosDepositoRetiro datos(Long cuentaId, String monto, String rol,
                                             Long clienteIdClaim) {
        return new DatosDepositoRetiro(cuentaId,
                monto == null ? null : new BigDecimal(monto), rol, clienteIdClaim);
    }

    private void stubCuentaActiva() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuenta(10L, 7L, EstadoCuenta.ACTIVA)));
    }

    private static Cuenta cuenta(Long id, Long clienteId, EstadoCuenta estado) {
        return cuenta(id, clienteId, estado, new BigDecimal("100000.00"));
    }

    private static Cuenta cuenta(Long id, Long clienteId, EstadoCuenta estado, BigDecimal saldo) {
        return new Cuenta(id, clienteId, new CBU("0000003100000000001234"),
                TipoCuenta.CAJA_AHORRO, Money.ars(saldo), ARS, estado, CREATED_AT, 0L);
    }
}
