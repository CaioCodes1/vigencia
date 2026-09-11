package com.caiocodes.vigencia.reporting.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Um cliente da lista de inadimplência.
 *
 * <p>O valor em atraso é o <b>saldo</b> — cobrança menos o que já foi pago e
 * não estornado — e não o valor da cobrança. Cobrar de novo o que o cliente já
 * pagou parcialmente é o jeito mais rápido de perder o cliente que ainda paga.
 */
public record DelinquentClient(
        UUID clientId,
        String legalName,
        long overdueBillings,
        BigDecimal totalOverdue,
        long oldestOverdueDays) {
}
