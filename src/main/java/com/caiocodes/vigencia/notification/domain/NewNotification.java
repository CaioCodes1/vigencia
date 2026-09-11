package com.caiocodes.vigencia.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Os dados de agendamento de um aviso.
 *
 * @param daysOffset janela: 30, 15, 7, 1 antes; negativo depois; nulo quando o
 *                   aviso não tem janela (renovação, recibo de pagamento)
 * @param payload    o que o template vai renderizar. Vira JSONB, e é o que
 *                   permite reconstruir meses depois exatamente o e-mail que
 *                   saiu
 */
public record NewNotification(
        NotificationType type,
        NotificationChannel channel,
        UUID clientId,
        UUID contractId,
        UUID billingId,
        Integer daysOffset,
        String recipient,
        String subject,
        Map<String, Object> payload,
        Instant scheduledAt) {
}
