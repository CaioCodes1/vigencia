package com.caiocodes.crbap.contract.infrastructure.persistence;

import com.caiocodes.crbap.contract.domain.BillingCycle;
import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractId;
import com.caiocodes.crbap.contract.domain.ContractListItem;
import com.caiocodes.crbap.contract.domain.ContractState;
import com.caiocodes.crbap.contract.domain.ContractStatus;
import com.caiocodes.crbap.contract.infrastructure.persistence.ContractEntity.BillingCycleValue;
import com.caiocodes.crbap.contract.infrastructure.persistence.ContractEntity.ContractStatusValue;
import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import java.util.Currency;
import org.springframework.stereotype.Component;

/** Tradução entre o agregado Contrato e o modelo de persistência. */
@Component
public class ContractPersistenceMapper {

    public Contract toDomain(ContractEntity entity) {
        return Contract.rehydrate(new ContractState(
                ContractId.of(entity.getId()),
                entity.getClientId(),
                entity.getNumber(),
                entity.getTitle(),
                entity.getDescription(),
                DateRange.of(entity.getStartDate(), entity.getEndDate()),
                money(entity),
                BillingCycle.valueOf(entity.getBillingCycle().name()),
                entity.getBillingDay() == null ? null : entity.getBillingDay().intValue(),
                entity.getGracePeriodDays(),
                ContractStatus.valueOf(entity.getStatus().name()),
                entity.isAutoRenew(),
                entity.getPreviousContractId() == null ? null
                        : ContractId.of(entity.getPreviousContractId()),
                entity.getCancellationReason(),
                entity.getCancelledAt(),
                entity.getActivatedAt(),
                entity.getIdempotencyKey(),
                entity.getCreatedBy(),
                entity.getVersion() == null ? 0L : entity.getVersion()));
    }

    public ContractListItem toListItem(ContractEntity entity) {
        return new ContractListItem(
                ContractId.of(entity.getId()),
                entity.getNumber(),
                entity.getClientId(),
                entity.getTitle(),
                DateRange.of(entity.getStartDate(), entity.getEndDate()),
                money(entity),
                BillingCycle.valueOf(entity.getBillingCycle().name()),
                ContractStatus.valueOf(entity.getStatus().name()),
                entity.isAutoRenew(),
                entity.getPreviousContractId() == null ? null
                        : ContractId.of(entity.getPreviousContractId()));
    }

    public void copyToEntity(Contract contract, ContractEntity entity) {
        entity.setId(contract.id().value());
        entity.setNumber(contract.number());
        entity.setClientId(contract.clientId());
        entity.setTitle(contract.title());
        entity.setDescription(contract.description());
        entity.setStartDate(contract.period().start());
        entity.setEndDate(contract.period().end());
        entity.setValueAmount(contract.value().amount());
        entity.setValueCurrency(contract.value().currency().getCurrencyCode());
        entity.setBillingCycle(BillingCycleValue.valueOf(contract.cycle().name()));
        entity.setBillingDay(contract.billingDay() == null ? null
                : contract.billingDay().shortValue());
        entity.setGracePeriodDays((short) contract.gracePeriodDays());
        entity.setStatus(ContractStatusValue.valueOf(contract.status().name()));
        entity.setAutoRenew(contract.autoRenew());
        entity.setPreviousContractId(contract.previousContractId() == null ? null
                : contract.previousContractId().value());
        entity.setCancellationReason(contract.cancellationReason());
        entity.setCancelledAt(contract.cancelledAt());
        entity.setActivatedAt(contract.activatedAt());
        entity.setIdempotencyKey(contract.idempotencyKey());
        entity.setCreatedBy(contract.createdBy());
    }

    private Money money(ContractEntity entity) {
        return Money.of(entity.getValueAmount(),
                Currency.getInstance(entity.getValueCurrency()));
    }
}
