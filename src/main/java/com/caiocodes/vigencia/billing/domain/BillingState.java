package com.caiocodes.vigencia.billing.domain;

import com.caiocodes.vigencia.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * O estado completo de uma cobrança, do jeito que ela saiu do banco.
 *
 * <p>Só o adaptador de persistência monta, só {@link Billing#rehydrate} consome.
 * Reidratar <b>não é</b> emitir: não valida transição e não gera evento.
 */
public record BillingState(
        BillingId id,
        UUID clientId,
        UUID contractId,
        String reference,
        Integer installment,
        Integer totalInstallments,
        Money amount,
        LocalDate dueDate,
        LocalDate issueDate,
        BillingStatus status,
        String notes,
        String cancellationReason,
        String idempotencyKey,
        List<Payment> payments,
        long version) {
}
