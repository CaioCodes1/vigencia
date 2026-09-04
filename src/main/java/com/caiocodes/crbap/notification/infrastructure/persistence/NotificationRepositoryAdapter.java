package com.caiocodes.crbap.notification.infrastructure.persistence;

import com.caiocodes.crbap.notification.domain.Notification;
import com.caiocodes.crbap.notification.domain.NotificationId;
import com.caiocodes.crbap.notification.domain.NotificationRepository;
import com.caiocodes.crbap.notification.domain.NotificationSearchCriteria;
import com.caiocodes.crbap.notification.infrastructure.persistence.NotificationEntity.NotificationStatusValue;
import com.caiocodes.crbap.notification.infrastructure.persistence.NotificationEntity.NotificationTypeValue;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de persistência de notificações. */
@Slf4j
@Repository
@RequiredArgsConstructor
class NotificationRepositoryAdapter implements NotificationRepository {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO notifications (id, type, channel, contract_id, billing_id, client_id,
                                       days_offset, recipient, subject, payload, status,
                                       attempts, scheduled_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 0, ?)
            ON CONFLICT DO NOTHING
            """;

    private final NotificationJpaRepository jpa;
    private final NotificationPersistenceMapper mapper;
    private final JdbcTemplate jdbc;

    @Override
    public Optional<Notification> findById(NotificationId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    /**
     * Grava tolerando o duplicado, com {@code ON CONFLICT DO NOTHING}.
     *
     * <p><b>Por que não é um {@code try/catch} em cima do
     * {@code DataIntegrityViolationException}:</b> no Postgres, o primeiro
     * comando que falha dentro de uma transação <b>envenena a transação
     * inteira</b> — todo comando seguinte responde
     * {@code current transaction is aborted, commands ignored until end of
     * transaction block}. Capturar a exceção não desfaz isso.
     *
     * <p>O efeito prático, que este projeto viu acontecer: o mesmo aviso vai
     * para dois destinatários (o contato do cliente e o gestor da conta). Na
     * segunda rodada do job, o primeiro {@code INSERT} esbarra no índice único e
     * é "tratado"; o segundo estoura com a transação abortada e derruba a
     * varredura inteira. O {@code try/catch} parecia funcionar porque o caso de
     * um destinatário só nunca chegava ao segundo {@code INSERT}.
     *
     * <p>{@code ON CONFLICT DO NOTHING} <b>não levanta erro</b>: o comando
     * simplesmente não insere, a transação segue limpa, e o número de linhas
     * afetadas diz o que aconteceu. Sem alvo de conflito declarado, ele cobre
     * os três índices únicos parciais da tabela de uma vez.
     *
     * <p>A alternativa seria {@code SAVEPOINT} por {@code INSERT}
     * ({@code PROPAGATION_NESTED}), que o {@code JpaTransactionManager} não
     * suporta — e que custaria uma ida a mais ao banco por aviso.
     */
    @Override
    public Optional<Notification> scheduleIfAbsent(Notification notification) {
        int inseridas = jdbc.update(INSERT_IF_ABSENT,
                notification.id().value(),
                notification.type().name(),
                notification.channel().name(),
                notification.contractId(),
                notification.billingId(),
                notification.clientId(),
                notification.daysOffset() == null ? null : notification.daysOffset().shortValue(),
                notification.recipient(),
                notification.subject(),
                mapper.serializePayload(notification.payload()),
                notification.status().name(),
                timestamp(notification.scheduledAt()));

        if (inseridas == 0) {
            log.debug("notification.ja_existia tipo={} janela={} para={}",
                    notification.type(), notification.daysOffset(), notification.recipient());
            return Optional.empty();
        }
        return Optional.of(notification);
    }

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = jpa.findById(notification.id().value())
                .orElseGet(NotificationEntity::new);
        mapper.copyToEntity(notification, entity);
        jpa.save(entity);
        return notification;
    }

    @Override
    public List<NotificationId> findPendingUntil(Instant now, int limit) {
        return jpa.findPendingUntil(NotificationStatusValue.PENDING, now,
                        PageRequest.of(0, limit)).stream()
                .map(NotificationId::of)
                .toList();
    }

    @Override
    public List<NotificationId> findPendingByContract(UUID contractId) {
        return jpa.findPendingByContract(contractId, NotificationStatusValue.PENDING).stream()
                .map(NotificationId::of)
                .toList();
    }

    @Override
    public PageResult<Notification> search(NotificationSearchCriteria criteria) {
        Page<NotificationEntity> page = jpa.search(
                criteria.type() == null ? null : NotificationTypeValue.valueOf(
                        criteria.type().name()),
                criteria.status() == null ? null : NotificationStatusValue.valueOf(
                        criteria.status().name()),
                criteria.clientId(),
                criteria.contractId(),
                criteria.from(),
                criteria.until(),
                PageRequest.of(criteria.page(), criteria.size()));

        return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
                criteria.page(), criteria.size(), page.getTotalElements());
    }

    /**
     * {@code OffsetDateTime} em UTC, e não {@code Timestamp}.
     *
     * <p>A coluna é {@code TIMESTAMPTZ}; {@code Timestamp} faria o driver usar o
     * fuso da JVM, e o mesmo instante viraria horários diferentes conforme a
     * máquina que rodou o job.
     */
    private static OffsetDateTime timestamp(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
