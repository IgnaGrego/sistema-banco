package com.banco.infrastructure.adapter.persistence;

import com.banco.domain.model.Movimiento;
import com.banco.domain.model.TipoMovimiento;
import com.banco.domain.port.MovimientoRepository;
import com.banco.domain.vo.Money;
import com.banco.domain.vo.Moneda;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador JPA del puerto {@link MovimientoRepository}. Mapeo explícito
 * Movimiento ↔ MovimientoJpaEntity (el monto se guarda como BigDecimal junto
 * con la moneda de la cuenta, sin AttributeConverter).
 */
@Component
public class MovimientoRepositoryAdapter implements MovimientoRepository {

    private final MovimientoJpaRepository jpaRepository;

    public MovimientoRepositoryAdapter(MovimientoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Movimiento save(Movimiento movimiento) {
        return toDomain(jpaRepository.save(toEntity(movimiento)));
    }

    @Override
    public List<Movimiento> findByCuentaIdOrderByFechaDesc(Long cuentaId) {
        return jpaRepository.findByCuentaIdOrderByFechaDesc(cuentaId).stream().map(this::toDomain).toList();
    }

    private MovimientoJpaEntity toEntity(Movimiento movimiento) {
        MovimientoJpaEntity entity = new MovimientoJpaEntity();
        entity.setId(movimiento.getId());
        entity.setCuentaId(movimiento.getCuentaId());
        entity.setTipo(movimiento.getTipo().name());
        entity.setMonto(movimiento.getMonto().monto());
        entity.setFecha(movimiento.getFecha());
        entity.setCuentaContraparteId(movimiento.getCuentaContraparteId());
        return entity;
    }

    private Movimiento toDomain(MovimientoJpaEntity entity) {
        // Moneda del movimiento: ARS (moneda del MVP — SPEC-004, BR-003).
        return new Movimiento(entity.getId(), entity.getCuentaId(),
                TipoMovimiento.valueOf(entity.getTipo()),
                new Money(entity.getMonto(), new Moneda("ARS")),
                entity.getFecha(), entity.getCuentaContraparteId());
    }
}
