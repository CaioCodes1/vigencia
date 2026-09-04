package com.caiocodes.crbap.shared.infrastructure.outbox;

import com.caiocodes.crbap.shared.infrastructure.persistence.OutboxEventEntity;
import com.caiocodes.crbap.shared.infrastructure.persistence.OutboxJpaRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tira os eventos da outbox e entrega ao publicador.
 *
 * <p>Roda a cada 5 segundos e trabalha em lotes: o custo de uma consulta que
 * não acha nada é desprezível, e a latência do aviso ao cliente fica em
 * segundos em vez de depender de alguém empurrar.
 *
 * <p><b>Entrega ao menos uma vez, nunca exatamente uma.</b> Se o publicador
 * entregar e o processo cair antes do commit, o evento sai de novo. É a garantia
 * que se consegue sem transação distribuída — e o motivo de todo consumidor ter
 * de ser idempotente (fase 6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crbap.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxJpaRepository outbox;
    private final DomainEventPublisher publisher;
    private final Clock clock;

    /**
     * <b>Sem {@code @SchedulerLock} de propósito</b>, ao contrário dos outros
     * jobs.
     *
     * <p>A trava do ShedLock é exclusão: uma instância roda, as outras pulam.
     * Aqui o {@code FOR UPDATE SKIP LOCKED} da consulta faz algo melhor — cada
     * instância pega um pedaço disjunto da fila e as duas trabalham em
     * paralelo. Pôr a trava por cima serializaria o relay e desfaria
     * exatamente o motivo de o {@code SKIP LOCKED} existir.
     */
    @Scheduled(fixedDelayString = "${crbap.jobs.outbox-relay-delay:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> lote = outbox.lockUnpublished(PageRequest.of(0, BATCH_SIZE));
        if (lote.isEmpty()) {
            return;
        }
        int publicados = 0;
        for (OutboxEventEntity event : lote) {
            event.setAttempts((short) (event.getAttempts() + 1));
            try {
                publisher.publish(event);
                event.setPublishedAt(clock.instant());
                event.setLastError(null);
                publicados++;
            } catch (RuntimeException e) {
                // Uma falha não derruba o lote: o evento fica sem publishedAt e
                // volta na próxima rodada. Sem isso, um payload problemático
                // travaria a fila inteira atrás dele.
                event.setLastError(truncate(e.getMessage()));
                log.warn("outbox.publish_failed eventId={} tentativa={}",
                        event.getId(), event.getAttempts(), e);
            }
        }
        log.debug("outbox.relay lote={} publicados={}", lote.size(), publicados);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "sem mensagem";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
