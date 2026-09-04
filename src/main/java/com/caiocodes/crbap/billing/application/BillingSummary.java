package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.billing.domain.BillingListItem;
import com.caiocodes.crbap.billing.domain.BillingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Linha da listagem de cobranças. */
public record BillingSummary(
        UUID id,
        UUID clientId,
        UUID contractId,
        String reference,
        Integer installment,
        Integer totalInstallments,
        BigDecimal amount,
        BigDecimal totalPaid,
        BigDecimal remaining,
        String currency,
        LocalDate dueDate,
        long daysLate,
        BillingStatus status) {

    public static BillingSummary from(BillingListItem item, LocalDate today) {
        return new BillingSummary(
                item.id().value(),
                item.clientId(),
                item.contractId(),
                item.reference(),
                item.installment(),
                item.totalInstallments(),
                item.amount().amount(),
                item.totalPaid().amount(),
                item.remaining().amount(),
                item.amount().currency().getCurrencyCode(),
                item.dueDate(),
                Math.max(0, ChronoUnit.DAYS.between(item.dueDate(), today)),
                item.status());
    }
}
