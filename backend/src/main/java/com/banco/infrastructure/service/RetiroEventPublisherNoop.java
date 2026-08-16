package com.banco.infrastructure.service;

import com.banco.domain.event.RetiroRealizado;
import com.banco.domain.port.RetiroEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emisor del evento {@link RetiroRealizado} sin suscriptores (FR-004): en este
 * sprint no hay auditoría/notificación, así que la implementación solo loguea
 * a nivel debug. La emisión se verifica en tests (AC-009, mock en unit test).
 */
@Component
public class RetiroEventPublisherNoop implements RetiroEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RetiroEventPublisherNoop.class);

    @Override
    public void publicar(RetiroRealizado evento) {
        log.debug("Evento RetiroRealizado emitido (sin suscriptores): monto={}, cuentaId={}, idMovimiento={}",
                evento.monto(), evento.cuentaId(), evento.idMovimiento());
    }
}
