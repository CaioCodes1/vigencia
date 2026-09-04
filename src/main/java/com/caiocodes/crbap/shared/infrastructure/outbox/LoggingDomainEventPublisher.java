package com.caiocodes.crbap.shared.infrastructure.outbox;

import com.caiocodes.crbap.shared.infrastructure.persistence.OutboxEventEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Publicador provisório: escreve o evento no log estruturado.
 *
 * <p>Não é placebo — com o Loki (nota 12) dá para responder "quais eventos
 * saíram ontem" sem broker nenhum, e o relay já exercita o caminho inteiro
 * (marcar publicado, contar tentativa, registrar erro).
 *
 * <p><b>Na fase 6 esta classe é apagada</b> e o publicador do RabbitMQ assume.
 * Não há {@code @ConditionalOnMissingBean} aqui de propósito: dois publicadores
 * vivos ao mesmo tempo é o tipo de ambiguidade que se descobre em produção —
 * melhor o contexto se recusar a subir com dois beans do mesmo tipo.
 */
@Slf4j
@Component
public class LoggingDomainEventPublisher implements DomainEventPublisher {

    @Override
    public void publish(OutboxEventEntity event) {
        log.info("event.published eventType={} aggregateType={} aggregateId={} payload={}",
                event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                event.getPayload());
    }
}
