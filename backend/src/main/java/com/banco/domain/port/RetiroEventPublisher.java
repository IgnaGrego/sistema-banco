package com.banco.domain.port;

import com.banco.domain.event.RetiroRealizado;

/**
 * Abstracción de la emisión del evento de dominio {@link RetiroRealizado}
 * (FR-004): permite que la capa de aplicación lo publique sin depender de
 * infraestructura. En este sprint no tiene suscriptores; la emisión se
 * verifica en tests (AC-009).
 */
public interface RetiroEventPublisher {

    void publicar(RetiroRealizado evento);
}
