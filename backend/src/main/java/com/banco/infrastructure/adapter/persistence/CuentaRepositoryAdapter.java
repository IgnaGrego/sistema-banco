package com.banco.infrastructure.adapter.persistence;

import com.banco.domain.model.Cuenta;
import com.banco.domain.model.EstadoCuenta;
import com.banco.domain.model.TipoCuenta;
import com.banco.domain.port.CuentaRepository;
import com.banco.domain.vo.CBU;
import com.banco.domain.vo.Money;
import com.banco.domain.vo.Moneda;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link CuentaRepository}. Mapeo explícito
 * Cuenta ↔ CuentaJpaEntity (enums como String, VOs mapeados manualmente, sin
 * AttributeConverter) con la {@code version} mapeada en ambos sentidos —
 * crítica para el optimistic lock (docs/architecture/SPEC-004.md §8.8).
 *
 * La agregación del límite diario (BR-004) vive aquí por mandato de la spec
 * §10, pero los datos viven en {@code movimientos}: se delega en
 * {@link MovimientoJpaRepository} (§8.10).
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
        return jpaRepository.findByClienteId(clienteId).stream().map(this::toDomain).toList();
    }

    @Override
    public Money montoTotalTransferenciasSalientesDelDia(Long clienteId, LocalDate dia) {
        // "Día calendario" en UTC (convención de almacenamiento del repo — V1).
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
        entity.setMoneda(cuenta.getMoneda().name());
        entity.setEstado(cuenta.getEstado().name());
        entity.setCreatedAt(cuenta.getCreatedAt());
        entity.setVersion(cuenta.getVersion());
        return entity;
    }

    private Cuenta toDomain(CuentaJpaEntity entity) {
        return new Cuenta(entity.getId(), entity.getClienteId(), new CBU(entity.getCbu()),
                TipoCuenta.valueOf(entity.getTipo()),
                new Money(entity.getSaldo(), Currency.getInstance(entity.getMoneda())),
                Moneda.valueOf(entity.getMoneda()),
                EstadoCuenta.valueOf(entity.getEstado()),
                entity.getCreatedAt(),
                entity.getVersion());
    }
}
