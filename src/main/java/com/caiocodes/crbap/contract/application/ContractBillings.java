package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.contract.application.port.ContractBillingPort.ActivatedContract;
import com.caiocodes.crbap.contract.domain.Contract;

/**
 * Traduz o agregado {@code Contract} para o que a porta de cobranças aceita.
 *
 * <p>Uma classe só para isso porque a tradução acontece em dois lugares
 * (ativação e renovação) e precisa dar exatamente o mesmo resultado nos dois: um
 * contrato renovado que gerasse parcelas diferentes de um contrato ativado com
 * os mesmos dados seria um bug encontrado meses depois, na conciliação.
 */
final class ContractBillings {

    private ContractBillings() {
    }

    static ActivatedContract of(Contract contract) {
        return new ActivatedContract(
                contract.id().value(),
                contract.clientId(),
                contract.number(),
                contract.period(),
                contract.value(),
                contract.cycle(),
                contract.billingDay(),
                contract.gracePeriodDays());
    }
}
