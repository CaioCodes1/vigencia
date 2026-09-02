package com.caiocodes.crbap.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Fato que já aconteceu no domínio — por isso o nome sempre vem no passado
 * ({@code ContractRenewed}, não {@code RenewContract}).
 *
 * <p>O agregado apenas registra o evento; quem publica é a camada de
 * infraestrutura, depois do commit, via outbox. O domínio não conhece RabbitMQ.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    UUID aggregateId();

    String aggregateType();

    /** Nome canônico usado como routing key, ex.: {@code contract.renewed}. */
    String eventType();

    /** Versão do contrato do evento. Consumidor externo depende disso. */
    default int version() {
        return 1;
    }
}
