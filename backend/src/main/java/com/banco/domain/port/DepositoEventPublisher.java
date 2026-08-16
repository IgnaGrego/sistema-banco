package com.banco.domain.port;

import com.banco.domain.event.DepositoRealizado;

/**
 * Abstracción de la emisión del evento de dominio {@link DepositoRealizado}
 * (FR-004): permite que la capa de aplicación lo publique sin depender de
 * infraestructura (mismo patrón que {@code TransferenciaEventPublisher}).
 * En este sprint no tiene suscriptores; la emisión se verifica en tests
 * (AC-002).
 */
public interface DepositoEventPublisher {

    void publicar(DepositoRealizado evento);
}
