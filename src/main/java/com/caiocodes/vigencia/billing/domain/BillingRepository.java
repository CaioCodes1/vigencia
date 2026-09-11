package com.caiocodes.vigencia.billing.domain;

import com.caiocodes.vigencia.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de cobranças. */
public interface BillingRepository {

    Optional<Billing> findById(BillingId id);

    Optional<Billing> findByIdInScope(BillingId id, UUID accountManagerId);

    /**
     * Carrega com {@code SELECT ... FOR UPDATE}.
     *
     * <p>Todo caso de uso que mexe em dinheiro passa por aqui: dois pagamentos
     * simultâneos sobre a mesma cobrança leriam o mesmo saldo e os dois
     * passariam pela checagem de "excede o saldo".
     */
    Optional<Billing> findByIdForUpdate(BillingId id);

    /** Idempotência do pagamento: a chave é do pagamento, não da cobrança. */
    Optional<Billing> findByPaymentIdempotencyKey(String idempotencyKey);

    /**
     * De qual cobrança é este pagamento.
     *
     * <p>O estorno chega pelo id do pagamento, que é o que o usuário tem na mão
     * ao olhar um extrato — mas quem guarda o invariante do saldo é a cobrança.
     */
    Optional<BillingId> findByPaymentId(UUID paymentId);

    Optional<Billing> findByIdempotencyKey(String idempotencyKey);

    boolean existsByContractIdAndInstallment(UUID contractId, int installment);

    PageResult<BillingListItem> search(BillingSearchCriteria criteria);

    /** As que passaram do vencimento e ainda podem receber. Entrada do job. */
    List<BillingId> findOpenPastDue(LocalDate today);

    /** As em aberto de um contrato, com vencimento a partir de uma data. */
    List<BillingId> findOpenByContractDueFrom(UUID contractId, LocalDate from);

    Billing save(Billing billing);

    List<Billing> saveAll(List<Billing> billings);
}
