package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import java.util.UUID;

/**
 * Os dados de nascimento de um contrato.
 *
 * <p>Existe para a fábrica {@link Contract#create} não virar um construtor de
 * onze argumentos posicionais — o tipo de assinatura em que trocar
 * {@code billingDay} por {@code gracePeriodDays} compila sem reclamar e só
 * aparece em produção.
 */
public record NewContract(
        UUID clientId,
        String number,
        String title,
        String description,
        DateRange period,
        Money value,
        BillingCycle cycle,
        Integer billingDay,
        int gracePeriodDays,
        boolean autoRenew,
        UUID createdBy) {
}
