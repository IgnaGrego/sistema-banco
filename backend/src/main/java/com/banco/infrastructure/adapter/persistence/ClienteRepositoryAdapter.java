package com.banco.infrastructure.adapter.persistence;

import com.banco.domain.model.Cliente;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.vo.DNI;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link ClienteRepository}. Mapeo explícito
 * Cliente ↔ ClienteJpaEntity (el DNI se guarda como String; sin
 * AttributeConverter, menos clases).
 */
@Component
public class ClienteRepositoryAdapter implements ClienteRepository {

    private final ClienteJpaRepository jpaRepository;

    public ClienteRepositoryAdapter(ClienteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Cliente save(Cliente cliente) {
        return toDomain(jpaRepository.save(toEntity(cliente)));
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Cliente> findAll() {
        return jpaRepository.findAllByOrderByIdAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByDni(DNI dni) {
        return jpaRepository.existsByDni(dni.valor());
    }

    @Override
    public boolean existsByDniAndIdNot(DNI dni, Long id) {
        return jpaRepository.existsByDniAndIdNot(dni.valor(), id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, Long id) {
        return jpaRepository.existsByEmailAndIdNot(email, id);
    }

    private ClienteJpaEntity toEntity(Cliente cliente) {
        ClienteJpaEntity entity = new ClienteJpaEntity();
        entity.setId(cliente.getId());
        entity.setNombre(cliente.getNombre());
        entity.setApellido(cliente.getApellido());
        entity.setDni(cliente.getDni().valor());
        entity.setEmail(cliente.getEmail());
        entity.setTelefono(cliente.getTelefono());
        entity.setFechaAlta(cliente.getFechaAlta());
        return entity;
    }

    private Cliente toDomain(ClienteJpaEntity entity) {
        return new Cliente(entity.getId(), entity.getNombre(), entity.getApellido(),
                new DNI(entity.getDni()), entity.getEmail(), entity.getTelefono(), entity.getFechaAlta());
    }
}
