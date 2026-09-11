package com.caiocodes.vigencia.shared.application;

import com.caiocodes.vigencia.shared.domain.AggregateRoot;
import com.caiocodes.vigencia.shared.domain.DomainEvent;
import java.util.List;

/**
 * Onde os eventos que o agregado acumulou vão parar.
 *
 * <p>A porta é declarada aqui, e não na infraestrutura, porque quem chama é o
 * caso de uso — e caso de uso não conhece adaptador. O único implementador hoje
 * grava na tabela {@code outbox_events}, dentro da mesma transação que mudou o
 * agregado.
 *
 * <p><b>Por que outbox e não publicar direto no broker:</b> publicar dentro do
 * caso de uso cria dois pontos de falha que não dá para coordenar. Ou o e-mail
 * de renovação sai e o banco faz rollback (cliente avisado de uma renovação que
 * não existe), ou o banco grava e o broker está fora do ar (renovação sem aviso
 * nenhum). Com a outbox existe <b>um commit só</b>: o fato e a intenção de
 * publicá-lo são gravados juntos, e a entrega vira problema de outro processo.
 */
public interface DomainEventRecorder {

    void recordAll(List<DomainEvent> events);

    /** Puxa os eventos pendentes do agregado e grava todos. */
    default void record(AggregateRoot<?> aggregate) {
        recordAll(aggregate.pullEvents());
    }
}
