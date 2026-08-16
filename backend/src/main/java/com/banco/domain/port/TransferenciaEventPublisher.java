package com.banco.domain.port;

import com.banco.domain.event.TransferenciaRealizada;

/**
 * Abstracción de la emisión del evento de dominio {@link TransferenciaRealizada}
 * (FR-004): permite que la capa de aplicación lo publique sin depender de
 * infraestructura (mismo patrón que {@code TokenEmisor}/{@code PasswordHasher}).
 * En este sprint no tiene suscriptores; la emisión se verifica en tests
 * (AC-003).
 */
public interface TransferenciaEventPublisher {

    void publicar(TransferenciaRealizada evento);
}
