package com.caiocodes.vigencia.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** O estado completo de um aviso, do jeito que ele saiu do banco. */
public record NotificationState(
        NotificationId id,
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
}
