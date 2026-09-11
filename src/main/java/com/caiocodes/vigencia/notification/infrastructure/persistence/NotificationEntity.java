package com.caiocodes.vigencia.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Modelo de persistência da notificação.
 *
 * <p>Sem {@code @Version}: notificação não sofre edição concorrente. O que
 * poderia acontecer — duas instâncias enviando o mesmo aviso — é resolvido no
 * {@code NotificationDispatcher}, que relê o registro dentro da transação e
 * desiste se ele já não estiver {@code PENDING}.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationTypeValue type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelValue channel;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "billing_id")
    private UUID billingId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "days_offset")
    private Short daysOffset;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 200)
    private String subject;

    /**
     * O conteúdo congelado no agendamento. {@code jsonb} para que dê para
     * consultar depois ({@code payload->>'contractNumber'}) sem decodificar a
     * tabela inteira — é o que responde "que e-mail exatamente saiu em março?".
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatusValue status;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Espelham os CHECKs da migração; os enums de domínio moram em notification.domain. */
    public enum NotificationTypeValue {
        CONTRACT_EXPIRING, CONTRACT_EXPIRED, CONTRACT_RENEWED,
        BILLING_CREATED, BILLING_DUE_SOON, BILLING_OVERDUE, PAYMENT_RECEIVED
    }

    public enum NotificationChannelValue {
        EMAIL, SMS, WEBHOOK, IN_APP
    }

    public enum NotificationStatusValue {
        PENDING, SENT, FAILED, CANCELLED
    }
}
