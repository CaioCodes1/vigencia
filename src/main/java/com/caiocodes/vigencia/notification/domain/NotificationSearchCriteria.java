package com.caiocodes.vigencia.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros da consulta de notificações.
 *
 * <p>Esta é a tela que responde "vocês me avisaram?". Por isso ela filtra por
 * cliente e por período, e não por carteira: quem tem {@code notification:read}
 * está investigando um caso, não navegando na própria lista.
 */
public record NotificationSearchCriteria(
        NotificationType type,
        NotificationStatus status,
        UUID clientId,
        UUID contractId,
        Instant from,
        Instant until,
        int page,
        int size) {

    private static final int MAX_SIZE = 100;

    public NotificationSearchCriteria {
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
    }
}
