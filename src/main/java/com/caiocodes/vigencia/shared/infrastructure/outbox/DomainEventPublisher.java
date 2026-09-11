package com.caiocodes.vigencia.shared.infrastructure.outbox;

import com.caiocodes.vigencia.shared.infrastructure.persistence.OutboxEventEntity;

/**
 * Para onde o evento sai depois de gravado.
 *
 * <p>Esta interface é a costura da fase 6: hoje o único implementador escreve no
 * log; quando o RabbitMQ entrar, troca-se o bean e nada mais muda — nem o
 * agregado, nem o caso de uso, nem o relay. Vale a abstração justamente porque
 * já se sabe que vai existir um segundo implementador.
 */
public interface DomainEventPublisher {

    void publish(OutboxEventEntity event);
}
