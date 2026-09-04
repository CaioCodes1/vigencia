package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.BillingCycle;
import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import java.util.UUID;

/**
 * Projeção de leitura da listagem de contratos.
 *
 * <p>A listagem não devolve o agregado: numa página de 20 contratos, carregar
 * {@code Contract} inteiro traria campos que a tela não mostra e abriria a
 * porta para alguém chamar {@code renew()} a partir de um resultado de busca.
 * Leitura e escrita têm modelos diferentes de propósito.
 */
public record ContractListItem(
        ContractId id,
        String number,
        UUID clientId,
        String title,
        DateRange period,
        Money value,
        BillingCycle cycle,
        ContractStatus status,
        boolean autoRenew,
        ContractId previousContractId) {
}
