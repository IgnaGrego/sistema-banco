package com.banco.application;

import com.banco.application.validator.DatosTransferencia;
import com.banco.application.validator.TransferValidator;
import com.banco.application.validator.TransferenciaValidada;
import com.banco.domain.exception.AccesoDenegadoException;
import com.banco.domain.exception.AutoTransferenciaException;
import com.banco.domain.exception.CuentaBloqueadaException;
import com.banco.domain.exception.CuentaNoEncontradaException;
import com.banco.domain.exception.DatosInvalidosException;
import com.banco.domain.exception.LimiteDiarioExcedidoException;
import com.banco.domain.exception.MonedaIncompatibleException;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cadena de validación de la transferencia (AC-004..AC-015 lógica, AC-021):
 * un caso por chequeo, en el ORDEN exacto del main flow (spec §6 paso 2,
 * docs/architecture/SPEC-004.md §8.4). La cadena corta ante el primer error.
 * {@code limiteDiario} inyectado = ARS 200000 (default — A-002).
 */
class TransferValidatorTest {

    private static final CBU CBU_ORIGEN = new CBU("0000003100000000001234");
    private static final CBU CBU_DESTINO = new CBU("0000003100000000005678");
    private static final Money LIMITE_DIARIO = Money.ars(new BigDecimal("200000"));
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    private CuentaRepository cuentaRepository;
    private TransferValidator validator;

    @BeforeEach
    void setUp() {
        cuentaRepository = mock(CuentaRepository.class);
        validator = new TransferValidator(cuentaRepository, LIMITE_DIARIO);
    }

    // --- Chequeo 1: la cuenta origen existe (ERR-002 → 404) ---

    @Test
    void origenInexistenteLanzaCuentaNoEncontrada() {
        when(cuentaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> validator.validar(datos(99L, CBU_DESTINO.valor(), "100.00", 7L)));
    }

    // --- Chequeo 2: la cuenta origen pertenece al cliente (ERR-006 → 403) ---

    @Test
    void origenAjenoLanzaAccesoDenegado() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));

        assertThrows(AccesoDenegadoException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.00", 999L)));
    }

    @Test
    void origenSinClienteIdClaimLanzaAccesoDenegado() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));

        assertThrows(AccesoDenegadoException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.00", null)));
    }

    // --- Chequeo 3: la cuenta origen está ACTIVA (ERR-003 → 422) ---

    @Test
    void origenBloqueadaLanzaCuentaBloqueada() {
        Cuenta origenBloqueada = cuenta(CBU_ORIGEN, 10L, 7L, EstadoCuenta.BLOQUEADA,
                new BigDecimal("1000.00"), new Moneda("ARS"));
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(origenBloqueada));

        assertThrows(CuentaBloqueadaException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.00", 7L)));
    }

    // --- Chequeo 4: CBU destino con formato válido (400 cbuDestino) y existe
    //     (ERR-002 → 404) ---

    @Test
    void cbuDestinoMalformadoLanzaDatosInvalidosConCampoCbuDestino() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(datos(10L, "123", "100.00", 7L)));

        assertEquals("cbuDestino", ex.getCampo());
    }

    @Test
    void cbuDestinoInexistenteLanzaCuentaNoEncontrada() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));
        when(cuentaRepository.findByCbu(CBU_DESTINO)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.00", 7L)));
    }

    // --- Chequeo 5: el destino es distinto del origen (ERR-008 → 422) ---

    @Test
    void cbuDestinoIgualAlOrigenLanzaAutoTransferencia() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));
        when(cuentaRepository.findByCbu(CBU_ORIGEN)).thenReturn(Optional.of(cuentaOrigen()));

        assertThrows(AutoTransferenciaException.class,
                () -> validator.validar(datos(10L, CBU_ORIGEN.valor(), "100.00", 7L)));
    }

    // --- Chequeo 6: la cuenta destino está ACTIVA (ERR-003 → 422) ---

    @Test
    void destinoBloqueadaLanzaCuentaBloqueada() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));
        Cuenta destinoBloqueada = cuenta(CBU_DESTINO, 20L, 8L, EstadoCuenta.BLOQUEADA,
                new BigDecimal("0.00"), new Moneda("ARS"));
        when(cuentaRepository.findByCbu(CBU_DESTINO)).thenReturn(Optional.of(destinoBloqueada));

        assertThrows(CuentaBloqueadaException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.00", 7L)));
    }

    // --- Chequeo 7: monto > 0, hasta 2 decimales (ERR-004 → 400) ---

    @Test
    void montoCeroLanzaDatosInvalidosConCampoMonto() {
        stubOrigenYDestinoValidos();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "0", 7L)));

        assertEquals("monto", ex.getCampo());
    }

    @Test
    void montoNegativoLanzaDatosInvalidosConCampoMonto() {
        stubOrigenYDestinoValidos();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "-5.00", 7L)));

        assertEquals("monto", ex.getCampo());
    }

    @Test
    void montoNullLanzaDatosInvalidosConCampoMonto() {
        stubOrigenYDestinoValidos();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), null, 7L)));

        assertEquals("monto", ex.getCampo());
    }

    @Test
    void montoConMasDe2DecimalesLanzaDatosInvalidosConCampoMonto() {
        stubOrigenYDestinoValidos();

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.123", 7L)));

        assertEquals("monto", ex.getCampo());
    }

    // --- Chequeo 8: monedas compatibles (ERR-009 → 422, AC-015) ---

    @Test
    void monedasDistintasLanzanMonedaIncompatible() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));
        Cuenta destinoUsd = cuenta(CBU_DESTINO, 20L, 8L, EstadoCuenta.ACTIVA,
                new BigDecimal("0.00"), new Moneda("USD"));
        when(cuentaRepository.findByCbu(CBU_DESTINO)).thenReturn(Optional.of(destinoUsd));

        assertThrows(MonedaIncompatibleException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "100.00", 7L)));
    }

    // --- Chequeo 9: saldo suficiente en origen (ERR-001 → 422) ---

    @Test
    void saldoInsuficienteLanzaSaldoInsuficiente() {
        // Origen con saldo 100 < monto 150.
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(
                cuenta(CBU_ORIGEN, 10L, 7L, EstadoCuenta.ACTIVA, new BigDecimal("100.00"),
                        new Moneda("ARS"))));
        when(cuentaRepository.findByCbu(CBU_DESTINO)).thenReturn(Optional.of(cuentaDestino()));

        assertThrows(SaldoInsuficienteException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "150.00", 7L)));
    }

    // --- Chequeo 10: límite diario del cliente (ERR-007 → 422) ---

    @Test
    void totalDelDiaMasMontoAlcanzaElLimiteYLanzaLimiteDiarioExcedido() {
        stubOrigenYDestinoValidos();
        // total 190000 + monto 10000 = 200000 >= 200000 → rechazado (AF-002/AC-010).
        when(cuentaRepository.montoTotalTransferenciasSalientesDelDia(eq(7L), any(LocalDate.class)))
                .thenReturn(Money.ars(new BigDecimal("190000")));

        assertThrows(LimiteDiarioExcedidoException.class,
                () -> validator.validar(datos(10L, CBU_DESTINO.valor(), "10000.00", 7L)));
    }

    @Test
    void totalDelDiaMasMontoJustoBajoElLimiteEsValido() {
        stubOrigenYDestinoValidos();
        when(cuentaRepository.montoTotalTransferenciasSalientesDelDia(eq(7L), any(LocalDate.class)))
                .thenReturn(Money.ars(new BigDecimal("189999.99")));

        TransferenciaValidada validada = validator.validar(
                datos(10L, CBU_DESTINO.valor(), "10000.00", 7L));

        assertEquals(new BigDecimal("10000.00"), validada.monto().monto());
    }

    // --- Happy path + trim de cbuDestino ---

    @Test
    void happyPathDevuelveCuentasCargadasYMontoValidado() {
        stubOrigenYDestinoValidos();
        when(cuentaRepository.montoTotalTransferenciasSalientesDelDia(eq(7L), any(LocalDate.class)))
                .thenReturn(Money.ars(BigDecimal.ZERO));

        TransferenciaValidada validada = validator.validar(
                datos(10L, "  " + CBU_DESTINO.valor() + "  ", "15000.00", 7L));

        // Las cuentas vienen cargadas (evita recargarlas en el use case) y el
        // monto ya es un Money ARS (BR-003).
        assertEquals(10L, validada.origen().getId());
        assertEquals(20L, validada.destino().getId());
        assertEquals(new BigDecimal("15000.00"), validada.monto().monto());
        assertEquals("ARS", validada.monto().moneda().codigo());
    }

    // --- Helpers ---

    private static DatosTransferencia datos(Long cuentaOrigenId, String cbuDestino,
                                            String monto, Long clienteIdClaim) {
        return new DatosTransferencia(cuentaOrigenId, cbuDestino,
                monto == null ? null : new BigDecimal(monto), clienteIdClaim);
    }

    private void stubOrigenYDestinoValidos() {
        when(cuentaRepository.findById(10L)).thenReturn(Optional.of(cuentaOrigen()));
        when(cuentaRepository.findByCbu(CBU_DESTINO)).thenReturn(Optional.of(cuentaDestino()));
    }

    private static Cuenta cuentaOrigen() {
        return cuenta(CBU_ORIGEN, 10L, 7L, EstadoCuenta.ACTIVA, new BigDecimal("100000.00"),
                new Moneda("ARS"));
    }

    private static Cuenta cuentaDestino() {
        return cuenta(CBU_DESTINO, 20L, 8L, EstadoCuenta.ACTIVA, new BigDecimal("0.00"),
                new Moneda("ARS"));
    }

    private static Cuenta cuenta(CBU cbu, Long id, Long clienteId, EstadoCuenta estado,
                                 BigDecimal saldo, Moneda moneda) {
        // Money con la moneda dada (BR-007 se evalúa por igualdad de
        // Money.moneda() — ADR-007).
        return new Cuenta(id, clienteId, cbu, TipoCuenta.CAJA_AHORRO, new Money(saldo, moneda), moneda,
                estado, CREATED_AT, 0L);
    }
}
