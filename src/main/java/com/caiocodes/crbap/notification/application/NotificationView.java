package com.caiocodes.crbap.notification.application;

import com.caiocodes.crbap.notification.domain.Notification;
import com.caiocodes.crbap.notification.domain.NotificationChannel;
import com.caiocodes.crbap.notification.domain.NotificationStatus;
import com.caiocodes.crbap.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A resposta da tela "vocês me avisaram?".
 *
 * <p>Devolve o {@code payload} inteiro de propósito: é o conteúdo congelado do
 * aviso, e é ele que encerra a discussão sobre o que foi comunicado. Sem isso a
 * tela mostraria "enviado em 01/09" sem poder dizer o que estava escrito.
 */
public record NotificationView(
        UUID id,
        NotificationType type,
        NotificationChannel channel,
        UUID clientId,
        UUID contractId,
        UUID billingId,
        Integer daysOffset,
        String recipient,
        String subject,
        Map<String, Object> payload,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant scheduledAt,
        Instant sentAt) {

    public static NotificationView from(Notification notification) {
        return new NotificationView(
                notification.id().value(),
                notification.type(),
                notification.channel(),
                notification.clientId(),
                notification.contractId(),
                notification.billingId(),
                notification.daysOffset(),
                notification.recipient(),
                notification.subject(),
                notification.payload(),
                notification.status(),
                notification.attempts(),
                notification.lastError(),
                notification.scheduledAt(),
                notification.sentAt());
    }
}
