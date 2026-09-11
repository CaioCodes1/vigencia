package com.caiocodes.vigencia.notification.domain;

import com.caiocodes.vigencia.shared.domain.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de notificações. */
public interface NotificationRepository {

    Optional<Notification> findById(NotificationId id);

    /**
     * Grava um aviso novo, <b>tolerando</b> o duplicado.
     *
     * <p>Devolve vazio quando o índice único recusou — que é o caso esperado
     * quando o job roda duas vezes. Quem chama não trata exceção: já veio
     * traduzido para "não agendei, porque já existia".
     */
    Optional<Notification> scheduleIfAbsent(Notification notification);

    Notification save(Notification notification);

    /** Os avisos prontos para sair. Entrada do job de envio. */
    List<NotificationId> findPendingUntil(Instant now, int limit);

    /** Avisos pendentes de um contrato — usado para cancelar em lote. */
    List<NotificationId> findPendingByContract(UUID contractId);

    PageResult<Notification> search(NotificationSearchCriteria criteria);
}
