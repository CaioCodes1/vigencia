package com.caiocodes.crbap.notification.infrastructure.persistence;

import com.caiocodes.crbap.notification.infrastructure.persistence.NotificationEntity.NotificationStatusValue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    /** Entrada do job de envio — usa o idx_notif_pending. */
    @Query("""
            SELECT n.id FROM NotificationEntity n
             WHERE n.status = :pendente AND n.scheduledAt <= :agora
             ORDER BY n.scheduledAt
            """)
    List<UUID> findPendingUntil(NotificationStatusValue pendente, Instant agora,
                                Pageable pageable);

    @Query("""
            SELECT n.id FROM NotificationEntity n
             WHERE n.contractId = :contractId AND n.status = :pendente
            """)
    List<UUID> findPendingByContract(UUID contractId, NotificationStatusValue pendente);

    /**
     * Consulta da tela "vocês me avisaram?".
     *
     * <p>JPQL, e não nativa: aqui não há join com clientes nem agregação — o
     * escopo de carteira não se aplica, porque quem tem {@code notification:read}
     * está investigando um caso específico, não navegando na própria lista.
     */
    @Query("""
            SELECT n FROM NotificationEntity n
             WHERE (:type IS NULL OR n.type = :type)
               AND (:status IS NULL OR n.status = :status)
               AND (:clientId IS NULL OR n.clientId = :clientId)
               AND (:contractId IS NULL OR n.contractId = :contractId)
               AND (CAST(:from AS timestamp) IS NULL OR n.createdAt >= :from)
               AND (CAST(:until AS timestamp) IS NULL OR n.createdAt <= :until)
             ORDER BY n.createdAt DESC
            """)
    Page<NotificationEntity> search(NotificationEntity.NotificationTypeValue type,
                                    NotificationStatusValue status,
                                    UUID clientId,
                                    UUID contractId,
                                    Instant from,
                                    Instant until,
                                    Pageable pageable);
}
