package com.banco.infrastructure.adapter.persistence;

import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Moneda;
import com.banco.domain.vo.Money;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link CuentaRepository}. Mapeo explícito
 * {@code Cuenta ↔ CuentaJpaEntity} (enums como String = {@code name()} /
 * {@code valueOf(...)}; {@code Money} ↔ {@code BigDecimal}; sin
 * {@code AttributeConverter} — convención de SPEC-001 §5.4). {@code version}
 * se mapea en ambas direcciones para que el optimistic lock funcione en
 * SPEC-004/005.
 *
 * <p>La agregación del límite diario (BR-004 de SPEC-004) delega en
 * {@link MovimientoJpaRepository}: el dato vive en la tabla {@code movimientos}
 * (docs/architecture/SPEC-004.md §8.10).
 */
@Component
public class CuentaRepositoryAdapter implements CuentaRepository {

    private final CuentaJpaRepository jpaRepository;
    private final MovimientoJpaRepository movimientoJpaRepository;

    public CuentaRepositoryAdapter(CuentaJpaRepository jpaRepository,
                                   MovimientoJpaRepository movimientoJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.movimientoJpaRepository = movimientoJpaRepository;
    }

    @Override
    public Cuenta save(Cuenta cuenta) {
        return toDomain(jpaRepository.save(toEntity(cuenta)));
    }

    @Override
    public Optional<Cuenta> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Cuenta> findByCbu(CBU cbu) {
        return jpaRepository.findByCbu(cbu.valor()).map(this::toDomain);
    }

    @Override
    public List<Cuenta> findByClienteId(Long clienteId) {
        return jpaRepository.findByClienteIdOrderByIdAsc(clienteId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Cuenta> findAll() {
        return jpaRepository.findAllByOrderByIdAsc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByCbu(CBU cbu) {
        return jpaRepository.existsByCbu(cbu.valor());
    }

    @Override
    public Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia) {
        // "Día calendario" en UTC: rango [inicio, fin) cubre las 24 h del día.
        Instant inicio = dia.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fin = dia.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Money.ars(movimientoJpaRepository.sumarTransferenciasSalientesDelDia(clienteId, inicio, fin));
    }

    private CuentaJpaEntity toEntity(Cuenta cuenta) {
        CuentaJpaEntity entity = new CuentaJpaEntity();
        entity.setId(cuenta.getId());
        entity.setClienteId(cuenta.getClienteId());
        entity.setCbu(cuenta.getCbu().valor());
        entity.setTipo(cuenta.getTipo().name());
        entity.setSaldo(cuenta.getSaldo().monto());
        entity.setMoneda(cuenta.getMoneda().codigo());
        entity.setEstado(cuenta.getEstado().name());
        entity.setVersion(cuenta.getVersion());
        entity.setCreatedAt(cuenta.getCreatedAt());
        return entity;
    }

    private Cuenta toDomain(CuentaJpaEntity entity) {
        Moneda moneda = new Moneda(entity.getMoneda());
        return new Cuenta(entity.getId(), entity.getClienteId(), new CBU(entity.getCbu()),
                TipoCuenta.valueOf(entity.getTipo()), new Money(entity.getSaldo(), moneda), moneda,
                EstadoCuenta.valueOf(entity.getEstado()), entity.getCreatedAt(), entity.getVersion());
    }
}
