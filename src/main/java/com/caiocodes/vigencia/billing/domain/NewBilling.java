package com.caiocodes.vigencia.billing.domain;

import com.caiocodes.vigencia.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os dados de emissão de uma cobrança.
 *
 * @param contractId    nulo em cobrança avulsa (multa, serviço extra)
 * @param installment   nulo em cobrança avulsa; anda junto com
 *                      {@code totalInstallments}
 * @param idempotencyKey opcional — presente quando a cobrança vem de uma
 *                       requisição que pode ser repetida
 */
public record NewBilling(
        UUID clientId,
        UUID contractId,
        String reference,
        Integer installment,
        Integer totalInstallments,
        Money amount,
        LocalDate dueDate,
        LocalDate issueDate,
        String notes,
        String idempotencyKey) {
}
