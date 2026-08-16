package com.banco.domain.port;

import com.banco.domain.model.Cuenta;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de cuentas (dominio puro; lo implementa un adaptador
 * en infrastructure).
 */
public interface CuentaRepository {

    Cuenta save(Cuenta cuenta);

    Optional<Cuenta> findById(Long id);

    Optional<Cuenta> findByCbu(CBU cbu);

    List<Cuenta> findByClienteId(Long clienteId);

    /**
     * Suma de los montos de los movimientos TRANSFERENCIA_SALIENTE del cliente
     * (TODAS sus cuentas) en el día calendario dado. Día interpretado en UTC
     * (convención de almacenamiento del repo — V1). Devuelve 0 (ARS) si no hay
     * movimientos (BR-004, docs/architecture/SPEC-004.md §8.10).
     */
    Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia);
}
