package com.caiocodes.crbap.shared.infrastructure.outbox;

import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import com.caiocodes.crbap.shared.domain.DomainEvent;
import com.caiocodes.crbap.shared.infrastructure.persistence.OutboxEventEntity;
import com.caiocodes.crbap.shared.infrastructure.persistence.OutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava os eventos do agregado na outbox.
 *
 * <p>{@code Propagation.MANDATORY} é a peça central: este método <b>exige</b>
 * uma transação já aberta e falha se for chamado fora de uma. É o que garante,
 * em tempo de execução, que o evento seja gravado no mesmo commit que a mudança
 * do agregado — sem isso, um refactor futuro poderia gravar o evento numa
 * transação própria e reintroduzir exatamente o problema que a outbox resolve.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder implements DomainEventRecorder {

    private final OutboxJpaRepository outbox;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAll(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setId(event.eventId() == null ? UUID.randomUUID() : event.eventId());
            entity.setAggregateType(event.aggregateType());
            entity.setAggregateId(event.aggregateId());
            entity.setEventType(event.eventType());
            entity.setEventVersion((short) event.version());
            entity.setPayload(serialize(event));
            entity.setOccurredAt(event.occurredAt());
            outbox.save(entity);
            log.debug("outbox.recorded eventType={} aggregateId={}",
                    event.eventType(), event.aggregateId());
        }
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Evento que não serializa é bug de programação, não falha de
            // ambiente: falhar a transação inteira é melhor que gravar o
            // agregado e perder o aviso ao cliente em silêncio.
            throw new IllegalStateException(
                    "Não foi possível serializar o evento " + event.eventType(), e);
        }
    }
}
