package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.contract.domain.ContractListItem;
import com.caiocodes.vigencia.contract.domain.ContractStatus;
import com.caiocodes.vigencia.shared.domain.BillingCycle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Linha da listagem de contratos. */
public record ContractSummary(
        UUID id,
        String number,
        UUID clientId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        long daysRemaining,
        BigDecimal amount,
        String currency,
        BillingCycle billingCycle,
        ContractStatus status,
        boolean autoRenew,
        UUID previousContractId) {

    public static ContractSummary from(ContractListItem item, LocalDate today) {
        return new ContractSummary(
                item.id().value(),
                item.number(),
                item.clientId(),
                item.title(),
                item.period().start(),
                item.period().end(),
                item.period().daysUntilEnd(today),
                item.value().amount(),
                item.value().currency().getCurrencyCode(),
                item.cycle(),
                item.status(),
                item.autoRenew(),
                item.previousContractId() == null ? null : item.previousContractId().value());
    }
}
