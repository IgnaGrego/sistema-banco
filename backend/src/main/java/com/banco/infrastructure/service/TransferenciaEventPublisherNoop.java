package com.banco.infrastructure.service;

import com.banco.domain.event.TransferenciaRealizada;
import com.banco.domain.port.TransferenciaEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emisor del evento {@link TransferenciaRealizada} sin suscriptores (FR-004):
 * en este sprint no hay auditoría/notificación, así que la implementación solo
 * loguea a nivel debug. La emisión se verifica en tests (AC-003, mock en unit
 * test).
 */
@Component
public class TransferenciaEventPublisherNoop implements TransferenciaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaEventPublisherNoop.class);

    @Override
    public void publicar(TransferenciaRealizada evento) {
        log.debug("Evento TransferenciaRealizada emitido (sin suscriptores): monto={}, origen={}, destino={}, idMovimientoSaliente={}",
                evento.monto(), evento.cbuOrigen(), evento.cbuDestino(), evento.idMovimientoSaliente());
    }
}
