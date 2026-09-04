package com.caiocodes.crbap.billing.infrastructure.persistence;

import com.caiocodes.crbap.billing.domain.Billing;
import com.caiocodes.crbap.billing.domain.BillingId;
import com.caiocodes.crbap.billing.domain.BillingListItem;
import com.caiocodes.crbap.billing.domain.BillingRepository;
import com.caiocodes.crbap.billing.domain.BillingSearchCriteria;
import com.caiocodes.crbap.billing.infrastructure.persistence.BillingEntity.BillingStatusValue;
import com.caiocodes.crbap.billing.infrastructure.persistence.BillingJpaRepository.BillingRow;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Adaptador de persistência de cobranças. */
@Repository
@RequiredArgsConstructor
class BillingRepositoryAdapter implements BillingRepository {

    /** Os estados que ainda podem receber dinheiro. */
    private static final List<BillingStatusValue> ABERTAS = List.of(
            BillingStatusValue.PENDING, BillingStatusValue.PARTIALLY_PAID,
            BillingStatusValue.OVERDUE);

    /** O job de inadimplência só olha o que ainda não venceu formalmente. */
    private static final List<BillingStatusValue> A_VENCER = List.of(
            BillingStatusValue.PENDING, BillingStatusValue.PARTIALLY_PAID);

    private final BillingJpaRepository jpa;
    private final BillingPersistenceMapper mapper;

    @Override
    public Optional<Billing> findById(BillingId id) {
        return jpa.findWithPaymentsById(id.value()).map(mapper::toDomain);
    }

    /**
     * O escopo é resolvido em <b>duas</b> consultas: a primeira filtra pelo
     * gestor no banco, a segunda carrega o agregado com os pagamentos.
     *
     * <p>Não dá para fazer numa só: o filtro precisa do join com
     * {@code clients} (quem tem gestor é o cliente), e consulta nativa não
     * carrega {@code EntityGraph}. Duas leituras por id são baratas; conferir o
     * gestor em memória depois de carregar seria de graça e errado — bastaria
     * um caminho novo esquecer da conferência.
     */
    @Override
    public Optional<Billing> findByIdInScope(BillingId id, UUID accountManagerId) {
        if (accountManagerId == null) {
            return findById(id);
        }
        return jpa.findIdInScope(id.value().toString(), accountManagerId.toString())
                .flatMap(jpa::findWithPaymentsById)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Billing> findByIdForUpdate(BillingId id) {
        // Trava a linha e, na sequência, carrega o agregado inteiro dentro da
        // mesma transação — ver a nota do EntityGraph no BillingJpaRepository.
        return jpa.findByIdForUpdate(id.value())
                .flatMap(e -> jpa.findWithPaymentsById(e.getId()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Billing> findByPaymentIdempotencyKey(String idempotencyKey) {
        return jpa.findBillingIdByPaymentIdempotencyKey(idempotencyKey)
                .flatMap(jpa::findWithPaymentsById)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<BillingId> findByPaymentId(UUID paymentId) {
        return jpa.findBillingIdByPaymentId(paymentId).map(BillingId::of);
    }

    @Override
    public Optional<Billing> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey)
                .flatMap(e -> jpa.findWithPaymentsById(e.getId()))
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByContractIdAndInstallment(UUID contractId, int installment) {
        return jpa.existsByContractIdAndInstallmentAndStatusNot(contractId,
                (short) installment, BillingStatusValue.CANCELLED);
    }

    @Override
    public PageResult<BillingListItem> search(BillingSearchCriteria criteria) {
        Page<BillingRow> page = jpa.search(
                criteria.status() == null ? null : criteria.status().name(),
                criteria.clientId() == null ? null : criteria.clientId().toString(),
                criteria.contractId() == null ? null : criteria.contractId().toString(),
                criteria.dueFrom(),
                criteria.dueUntil(),
                criteria.accountManagerId() == null ? null
                        : criteria.accountManagerId().toString(),
                PageRequest.of(criteria.page(), criteria.size()));

        return PageResult.of(page.getContent().stream().map(mapper::toListItem).toList(),
                criteria.page(), criteria.size(), page.getTotalElements());
    }

    @Override
    public List<BillingId> findOpenPastDue(LocalDate today) {
        return jpa.findIdsOpenPastDue(today, A_VENCER).stream().map(BillingId::of).toList();
    }

    @Override
    public List<BillingId> findOpenByContractDueFrom(UUID contractId, LocalDate from) {
        return jpa.findIdsOpenByContractDueFrom(contractId, from, ABERTAS).stream()
                .map(BillingId::of)
                .toList();
    }

    /** Devolve o agregado recebido — mesma escolha dos outros adaptadores. */
    @Override
    public Billing save(Billing billing) {
        BillingEntity entity = jpa.findWithPaymentsById(billing.id().value())
                .orElseGet(BillingEntity::new);
        mapper.copyToEntity(billing, entity);
        jpa.save(entity);
        return billing;
    }

    @Override
    public List<Billing> saveAll(List<Billing> billings) {
        // Um save por agregado, mas num flush só: com order_inserts e
        // batch_size: 50 no application.yml, as 12 parcelas viram um INSERT em
        // lote em vez de 12 idas ao banco.
        List<BillingEntity> entidades = billings.stream().map(billing -> {
            BillingEntity entity = new BillingEntity();
            mapper.copyToEntity(billing, entity);
            return entity;
        }).toList();
        jpa.saveAll(entidades);
        return billings;
    }
}
