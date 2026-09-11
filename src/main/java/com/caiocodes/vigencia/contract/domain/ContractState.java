package com.caiocodes.vigencia.contract.domain;

import com.caiocodes.vigencia.shared.domain.BillingCycle;
import com.caiocodes.vigencia.shared.domain.DateRange;
import com.caiocodes.vigencia.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * O estado completo de um contrato, do jeito que ele saiu do banco.
 *
 * <p>Só o adaptador de persistência monta este record, e só
 * {@link Contract#rehydrate} o consome: é a única porta por onde um contrato
 * pode ser reconstruído em qualquer estado, inclusive nos que nenhum método de
 * negócio alcançaria diretamente. Manter isso num tipo explícito deixa claro
 * que reidratar <b>não é</b> criar — não valida transição e não emite evento.
 */
public record ContractState(
        ContractId id,
        UUID clientId,
        String number,
        String title,
        String description,
        DateRange period,
        Money value,
        BillingCycle cycle,
        Integer billingDay,
        int gracePeriodDays,
        ContractStatus status,
        boolean autoRenew,
        ContractId previousContractId,
        String cancellationReason,
        Instant cancelledAt,
        Instant activatedAt,
        String idempotencyKey,
        UUID createdBy,
        long version) {
}
