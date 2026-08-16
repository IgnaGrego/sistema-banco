package com.banco.application.usecase;

import com.banco.application.command.AbrirCuentaCommand;
import com.banco.application.validator.AperturaValidator;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.exception.MonedaNoSoportadaException;
import com.banco.domain.factory.CuentaFactory;
import com.banco.domain.model.Cuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Apertura de cuenta (FR-001): valida la forma (400), aplica el default de
 * moneda (ARS — FR-008/A-005), rechaza monedas no soportadas (422, BR-005),
 * verifica que el titular exista (404, ERR-002), genera un CBU único con
 * regeneración ante colisión (AC-028, A-002) y persiste (UNIQUE (cbu) como
 * backstop → 409, ERR-010). Java puro, sin Spring.
 */
public class AbrirCuentaUseCase {

    private static final String BANCO = "00000001"; // banco ficticio, 8 dígitos (A-002)
    private static final int MAX_INTENTOS = 5;
    private static final SecureRandom RANDOM = new SecureRandom(); // thread-safe, JDK

    private final CuentaRepository repository;
    private final ClienteRepository clienteRepository;
    private final AperturaValidator validator;

    public AbrirCuentaUseCase(CuentaRepository repository,
                              ClienteRepository clienteRepository,
                              AperturaValidator validator) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.validator = validator;
    }

    public Cuenta ejecutar(AbrirCuentaCommand command) {
        // 1. Validación de forma → 400 (ERR-001, AC-004): tipo inválido,
        //    moneda con formato inválido (ERR-001, A-005).
        validator.validar(command.tipo(), command.moneda());

        // 2. Tipo garantizado válido por el paso 1.
        TipoCuenta tipo = TipoCuenta.valueOf(command.tipo());

        // 3. Default de moneda ARS si ausente (FR-008/A-005). Sin trim:
        //    " ars " es formato inválido → 400 (garantizado por el paso 1).
        String codigoMoneda = (command.moneda() == null || command.moneda().isBlank())
                ? "ARS"
                : command.moneda();

        // 4. Formato garantizado por el paso 1.
        Moneda moneda = new Moneda(codigoMoneda);

        // 5. Moneda no soportada → 422 (BR-005, ERR-009, AC-003).
        if (!moneda.codigo().equals("ARS")) {
            throw new MonedaNoSoportadaException();
        }

        // 6. El Cliente titular debe existir → 404 (ERR-002, AC-005).
        if (clienteRepository.findById(command.clienteId()).isEmpty()) {
            throw new ClienteNoEncontradoException();
        }

        // 7. CBU único (AC-028, A-002).
        CBU cbu = generarCbuUnico();

        // 8. Factory por tipo (BR-004) con saldo 0, estado ACTIVA, createdAt now.
        Cuenta cuenta = CuentaFactory.crear(command.clienteId(), tipo, cbu, moneda, Instant.now());

        // 9. Persistir; UNIQUE (cbu) como backstop de carreras → 409 (ERR-010).
        return repository.save(cuenta);
    }

    /**
     * Genera un CBU de 22 dígitos (8 banco fijo + 14 aleatorios: 4 sucursal +
     * 10 cuenta) verificando la unicidad antes de persistir y regenerando ante
     * colisión. Tope de 5 intentos: espacio de 10^14 candidatos, prácticamente
     * inalcanzable; se falla de forma determinista (500) antes que persistir a
     * ciegas (la BD queda como backstop de carreras reales, no de colisiones
     * de pre-chequeo).
     */
    private CBU generarCbuUnico() {
        for (int i = 0; i < MAX_INTENTOS; i++) {
            StringBuilder sb = new StringBuilder(BANCO); // 8 dígitos (banco)
            for (int d = 0; d < 14; d++) { // 4 (sucursal) + 10 (cuenta)
                sb.append(RANDOM.nextInt(10));
            }
            CBU cbu = new CBU(sb.toString()); // 22 dígitos; el VO valida
            if (!repository.existsByCbu(cbu)) {
                return cbu;
            }
        }
        throw new IllegalStateException("No se pudo generar un CBU único");
    }
}
