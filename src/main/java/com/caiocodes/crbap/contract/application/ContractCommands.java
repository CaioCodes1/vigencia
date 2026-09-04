package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.contract.domain.ContractId;
import com.caiocodes.crbap.shared.domain.BillingCycle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Entradas dos casos de uso de contrato. */
public final class ContractCommands {

    private ContractCommands() {
    }

    /**
     * @param number se vier nulo, o número é gerado ({@code CT-<ano>-<seq>})
     */
    public record CreateContract(
            UUID clientId,
            String number,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            String currency,
            BillingCycle billingCycle,
            Integer billingDay,
            Integer gracePeriodDays,
            boolean autoRenew) {
    }

    public record UpdateContract(
            ContractId contractId,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            String currency,
            BillingCycle billingCycle,
            Integer billingDay,
            Integer gracePeriodDays,
            Boolean autoRenew) {
    }

    /**
     * O início do novo período não vem na requisição: por definição é o dia
     * seguinte ao fim do contrato anterior. Aceitá-lo abriria a porta para
     * cadeias com buraco ou com sobreposição.
     *
     * @param idempotencyKey vem do cabeçalho {@code Idempotency-Key}
     */
    public record RenewContract(
            ContractId contractId,
            LocalDate endDate,
            BigDecimal amount,
            String currency,
            String idempotencyKey) {
    }

    public record CancelContract(ContractId contractId, String reason) {
    }

    public record SuspendContract(ContractId contractId, String reason) {
    }
}
