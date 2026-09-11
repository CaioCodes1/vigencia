package com.caiocodes.vigencia.shared.infrastructure.outbox;

import com.caiocodes.vigencia.shared.infrastructure.messaging.RabbitTopology;
import com.caiocodes.vigencia.shared.infrastructure.persistence.OutboxEventEntity;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica no RabbitMQ o que o relay tirou da outbox.
 *
 * <p>Substituiu o publicador de log da fase 4, como estava previsto. Note o que
 * <b>não</b> mudou por causa disso: nem o agregado, nem o caso de uso, nem o
 * relay. Essa é a costura que a porta {@code DomainEventPublisher} existia para
 * criar.
 *
 * <p>O corpo vai como o JSON cru da outbox, sem passar por objeto Java. Assim o
 * que trafega é exatamente o que foi gravado — e um consumidor externo, escrito
 * em outra linguagem, lê a mesma coisa.
 *
 * <p><b>Se o broker estiver fora do ar, isto lança</b> — e é o comportamento
 * certo: o relay captura, marca a tentativa, e o evento continua na outbox para
 * a próxima rodada. Engolir a exceção aqui faria o evento ser marcado como
 * publicado sem ter saído.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitDomainEventPublisher implements DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(OutboxEventEntity event) {
        var mensagem = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                // O id da mensagem é o id do evento: é o que permite ao
                // consumidor deduplicar uma redelivery sem inventar chave.
                .setMessageId(event.getId().toString())
                .setHeader("eventType", event.getEventType())
                .setHeader("aggregateType", event.getAggregateType())
                .setHeader("aggregateId", event.getAggregateId().toString())
                .setHeader("eventVersion", event.getEventVersion())
                // Persistente: a mensagem vai para disco. Fila durable com
                // mensagem transiente perde tudo no restart do broker — as duas
                // coisas juntas, ou nenhuma garantia.
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();

        rabbitTemplate.send(RabbitTopology.EXCHANGE, event.getEventType(), mensagem);
        log.debug("event.published eventType={} aggregateId={}",
                event.getEventType(), event.getAggregateId());
    }
}
