package com.caiocodes.crbap.billing.domain;

import com.caiocodes.crbap.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção de leitura da listagem de cobranças.
 *
 * <p>{@code totalPaid} vem calculado pelo banco, com um {@code SUM} sobre os
 * pagamentos não estornados. Carregar o agregado para somar em memória traria N
 * listas de pagamentos numa página de 20 — o N+1 clássico, aqui num lugar onde
 * a tela mostra centenas de linhas.
 */
public record BillingListItem(
        BillingId id,
        UUID clientId,
        UUID contractId,
        String reference,
        Integer installment,
        Integer totalInstallments,
        Money amount,
        Money totalPaid,
        LocalDate dueDate,
        BillingStatus status) {

    public Money remaining() {
        return amount.subtract(totalPaid);
    }
}
