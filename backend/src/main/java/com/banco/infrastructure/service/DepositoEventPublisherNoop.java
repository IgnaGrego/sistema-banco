package com.banco.infrastructure.service;

import com.banco.domain.event.DepositoRealizado;
import com.banco.domain.port.DepositoEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emisor del evento {@link DepositoRealizado} sin suscriptores (FR-004): en
 * este sprint no hay auditoría/notificación, así que la implementación solo
 * loguea a nivel debug. La emisión se verifica en tests (AC-002, mock en unit
 * test).
 */
@Component
public class DepositoEventPublisherNoop implements DepositoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DepositoEventPublisherNoop.class);

    @Override
    public void publicar(DepositoRealizado evento) {
        log.debug("Evento DepositoRealizado emitido (sin suscriptores): monto={}, cuentaId={}, idMovimiento={}",
                evento.monto(), evento.cuentaId(), evento.idMovimiento());
    }
}
