package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.contract.domain.BillingCycle;
import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Visão do contrato para fora do domínio.
 *
 * <p>{@code daysRemaining} e {@code renewable} são <b>calculados na hora</b>, a
 * partir da data de referência que o caso de uso passa. É a mesma decisão do
 * enum de status não ter {@code EXPIRING_SOON}: nada que dependa do relógio é
 * guardado, senão fica errado sozinho quando o job não roda.
 */
public record ContractDetail(
        UUID id,
        String number,
        UUID clientId,
        String clientName,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        long daysRemaining,
        MoneyView value,
        BillingCycle billingCycle,
        Integer billingDay,
        int gracePeriodDays,
        ContractStatus status,
        boolean autoRenew,
        boolean renewable,
        UUID previousContractId,
        String cancellationReason,
        Instant cancelledAt,
        Instant activatedAt) {

    public record MoneyView(BigDecimal amount, String currency) {
    }

    public static ContractDetail from(Contract contract, String clientName, LocalDate today) {
        return new ContractDetail(
                contract.id().value(),
                contract.number(),
                contract.clientId(),
                clientName,
                contract.title(),
                contract.description(),
                contract.period().start(),
                contract.period().end(),
                contract.daysUntilEnd(today),
                new MoneyView(contract.value().amount(),
                        contract.value().currency().getCurrencyCode()),
                contract.cycle(),
                contract.billingDay(),
                contract.gracePeriodDays(),
                contract.status(),
                contract.autoRenew(),
                contract.isRenewable(today),
                contract.previousContractId() == null ? null
                        : contract.previousContractId().value(),
                contract.cancellationReason(),
                contract.cancelledAt(),
                contract.activatedAt());
    }
}
