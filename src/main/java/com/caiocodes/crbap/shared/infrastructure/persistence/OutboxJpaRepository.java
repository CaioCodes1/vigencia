package com.caiocodes.crbap.shared.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * O lote que o relay vai publicar.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED}: com duas instâncias da aplicação no ar,
     * as duas rodam o relay ao mesmo tempo. Sem o {@code SKIP LOCKED}, a segunda
     * ficaria bloqueada esperando a primeira — ou, pior, publicaria o mesmo
     * evento duas vezes. Com ele, cada instância pega um pedaço disjunto da fila
     * e as duas trabalham em paralelo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout",
            value = "-2"))
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.publishedAt IS NULL ORDER BY e.occurredAt")
    List<OutboxEventEntity> lockUnpublished(Pageable pageable);

    long countByPublishedAtIsNull();
}
