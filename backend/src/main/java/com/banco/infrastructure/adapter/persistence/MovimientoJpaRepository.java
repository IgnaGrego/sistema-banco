package com.banco.infrastructure.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Acceso Spring Data para movimientos.
 */
public interface MovimientoJpaRepository extends JpaRepository<MovimientoJpaEntity, Long> {

    /**
     * Historial de una cuenta, ordenado por fecha descendente (FR-005, A-006).
     */
    List<MovimientoJpaEntity> findByCuentaIdOrderByFechaDesc(Long cuentaId);

    /**
     * Suma de los montos de los movimientos TRANSFERENCIA_SALIENTE del cliente
     * (todas sus cuentas — subquery sobre {@code cuentas.cliente_id}) en el
     * rango del día dado (BR-004, docs/architecture/SPEC-004.md §8.10). El
     * rango se interpreta en UTC y cubre {@code [inicio, fin)}.
     */
    @Query("""
            SELECT COALESCE(SUM(m.monto), 0)
            FROM MovimientoJpaEntity m
            WHERE m.tipo = 'TRANSFERENCIA_SALIENTE'
              AND m.cuentaId IN (SELECT c.id FROM CuentaJpaEntity c WHERE c.clienteId = :clienteId)
              AND m.fecha >= :inicio AND m.fecha < :fin
            """)
    BigDecimal sumarTransferenciasSalientesDelDia(@Param("clienteId") Long clienteId,
                                                  @Param("inicio") Instant inicio,
                                                  @Param("fin") Instant fin);
}
