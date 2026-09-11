package com.caiocodes.vigencia.contract.infrastructure.persistence;

import com.caiocodes.vigencia.contract.domain.Contract;
import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.contract.domain.ContractListItem;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.contract.domain.ContractSearchCriteria;
import com.caiocodes.vigencia.contract.infrastructure.persistence.ContractEntity.ContractStatusValue;
import com.caiocodes.vigencia.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador de persistência de contratos. */
@Repository
@RequiredArgsConstructor
class ContractRepositoryAdapter implements ContractRepository {

    private final ContractJpaRepository jpa;
    private final ContractPersistenceMapper mapper;

    @Override
    public Optional<Contract> findById(ContractId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Contract> findByIdInScope(ContractId id, UUID accountManagerId) {
        if (accountManagerId == null) {
            return findById(id);
        }
        return jpa.findByIdAndAccountManagerId(id.toString(), accountManagerId.toString())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Contract> findByIdForUpdate(ContractId id) {
        return jpa.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Contract> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNumber(String number) {
        return jpa.existsByNumber(number);
    }

    @Override
    public boolean hasActiveContracts(UUID clientId) {
        return jpa.existsByClientIdAndStatus(clientId, ContractStatusValue.ACTIVE);
    }

    @Override
    public PageResult<ContractListItem> search(ContractSearchCriteria criteria) {
        Page<ContractEntity> page = jpa.search(
                criteria.term() == null ? null : "%" + criteria.term() + "%",
                criteria.status() == null ? null : criteria.status().name(),
                criteria.clientId() == null ? null : criteria.clientId().toString(),
                criteria.endingFrom(),
                criteria.endingUntil(),
                criteria.accountManagerId() == null ? null
                        : criteria.accountManagerId().toString(),
                PageRequest.of(criteria.page(), criteria.size()));

        return PageResult.of(page.getContent().stream().map(mapper::toListItem).toList(),
                criteria.page(), criteria.size(), page.getTotalElements());
    }

    @Override
    public List<ContractListItem> findChain(ContractId anyContractInChain) {
        return jpa.findChain(anyContractInChain.toString()).stream()
                .map(mapper::toListItem)
                .toList();
    }

    @Override
    public List<ContractId> findActiveExpiredOn(LocalDate today) {
        return jpa.findIdsByStatusAndEndDateBefore(ContractStatusValue.ACTIVE, today).stream()
                .map(ContractId::of)
                .toList();
    }

    @Override
    public List<ContractId> findAutoRenewableEndingOn(LocalDate today) {
        return jpa.findIdsAutoRenewableEndingOn(ContractStatusValue.ACTIVE, today).stream()
                .map(ContractId::of)
                .toList();
    }

    /** Devolve o agregado recebido — mesma escolha dos adaptadores do IAM. */
    @Override
    public Contract save(Contract contract) {
        ContractEntity entity = jpa.findById(contract.id().value())
                .orElseGet(ContractEntity::new);
        mapper.copyToEntity(contract, entity);
        jpa.save(entity);
        return contract;
    }
}
