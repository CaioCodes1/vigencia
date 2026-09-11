package com.caiocodes.vigencia.billing.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros da busca de cobranças.
 *
 * <p>Como em contratos, {@code accountManagerId} não é escolha do chamador: vem
 * de quem está autenticado. O gestor mora no cliente, então o adaptador resolve
 * com um join.
 */
public record BillingSearchCriteria(
        BillingStatus status,
        UUID clientId,
        UUID contractId,
        LocalDate dueFrom,
        LocalDate dueUntil,
        UUID accountManagerId,
        int page,
        int size) {

    private static final int MAX_SIZE = 100;

    public BillingSearchCriteria {
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
    }
}
