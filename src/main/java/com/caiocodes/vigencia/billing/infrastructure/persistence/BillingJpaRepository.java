package com.caiocodes.vigencia.billing.infrastructure.persistence;

import com.caiocodes.vigencia.billing.infrastructure.persistence.BillingEntity.BillingStatusValue;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface BillingJpaRepository extends JpaRepository<BillingEntity, UUID> {

    @EntityGraph(attributePaths = "payments")
    Optional<BillingEntity> findWithPaymentsById(UUID id);

    /**
     * {@code SELECT ... FOR UPDATE} sem {@code EntityGraph}.
     *
     * <p>Os dois juntos não funcionam: o Hibernate transforma o
     * {@code EntityGraph} num {@code LEFT JOIN}, e o Postgres recusa
     * {@code FOR UPDATE} sobre o lado nulável de um outer join. Trava-se a
     * cobrança e os pagamentos vêm na leitura seguinte, dentro da mesma
     * transação — que é o que importa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BillingEntity b WHERE b.id = :id")
    Optional<BillingEntity> findByIdForUpdate(UUID id);

    Optional<BillingEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p.billing.id FROM PaymentEntity p WHERE p.idempotencyKey = :key")
    Optional<UUID> findBillingIdByPaymentIdempotencyKey(String key);

    @Query("SELECT p.billing.id FROM PaymentEntity p WHERE p.id = :paymentId")
    Optional<UUID> findBillingIdByPaymentId(UUID paymentId);

    /**
     * O id da cobrança, se ela estiver na carteira daquele gestor.
     *
     * <p>Devolve só o id de propósito: o agregado é carregado em seguida com o
     * {@code EntityGraph} dos pagamentos, que não sobreviveria a esta consulta
     * nativa. O filtro continua vindo do banco, que é o que importa — conferir
     * o gestor em memória depois de carregar seria a mesma falha de sempre.
     */
    @Query(value = """
            SELECT b.id FROM billings b
              JOIN clients cl ON cl.id = b.client_id
             WHERE b.id = CAST(:id AS uuid)
               AND cl.account_manager_id = CAST(:managerId AS uuid)
            """, nativeQuery = true)
    Optional<UUID> findIdInScope(String id, String managerId);

    /** Parcela já gerada e não cancelada — é a checagem que torna a geração idempotente. */
    boolean existsByContractIdAndInstallmentAndStatusNot(UUID contractId, Short installment,
                                                        BillingStatusValue status);

    /** Entrada do job de inadimplência — usa o idx_billings_due_status. */
    @Query("""
            SELECT b.id FROM BillingEntity b
             WHERE b.dueDate < :today AND b.status IN :abertas
            """)
    List<UUID> findIdsOpenPastDue(LocalDate today, List<BillingStatusValue> abertas);

    @Query("""
            SELECT b.id FROM BillingEntity b
             WHERE b.contractId = :contractId AND b.dueDate >= :from AND b.status IN :abertas
            """)
    List<UUID> findIdsOpenByContractDueFrom(UUID contractId, LocalDate from,
                                            List<BillingStatusValue> abertas);

    /**
     * Busca da listagem, com o total pago somado <b>pelo banco</b>.
     *
     * <p>Nativa por dois motivos: o escopo de carteira exige o join com
     * {@code clients} (quem tem gestor é o cliente), e o {@code SUM} dos
     * pagamentos precisa vir na mesma consulta. Carregar o agregado para somar
     * em memória traria 20 listas de pagamentos numa página de 20 linhas.
     */
    @Query(value = """
            SELECT b.*, COALESCE(p.pago, 0) AS total_pago
              FROM billings b
              JOIN clients cl ON cl.id = b.client_id
              LEFT JOIN (SELECT billing_id, SUM(amount) AS pago
                           FROM payments WHERE NOT refunded
                          GROUP BY billing_id) p ON p.billing_id = b.id
             WHERE (CAST(:status AS text) IS NULL OR b.status = CAST(:status AS text))
               AND (CAST(:clientId AS uuid) IS NULL OR b.client_id = CAST(:clientId AS uuid))
               AND (CAST(:contractId AS uuid) IS NULL
                    OR b.contract_id = CAST(:contractId AS uuid))
               AND (CAST(:dueFrom AS date) IS NULL OR b.due_date >= CAST(:dueFrom AS date))
               AND (CAST(:dueUntil AS date) IS NULL OR b.due_date <= CAST(:dueUntil AS date))
               AND (CAST(:managerId AS uuid) IS NULL
                    OR cl.account_manager_id = CAST(:managerId AS uuid))
             ORDER BY b.due_date, b.reference
            """,
            countQuery = """
            SELECT count(*) FROM billings b
              JOIN clients cl ON cl.id = b.client_id
             WHERE (CAST(:status AS text) IS NULL OR b.status = CAST(:status AS text))
               AND (CAST(:clientId AS uuid) IS NULL OR b.client_id = CAST(:clientId AS uuid))
               AND (CAST(:contractId AS uuid) IS NULL
                    OR b.contract_id = CAST(:contractId AS uuid))
               AND (CAST(:dueFrom AS date) IS NULL OR b.due_date >= CAST(:dueFrom AS date))
               AND (CAST(:dueUntil AS date) IS NULL OR b.due_date <= CAST(:dueUntil AS date))
               AND (CAST(:managerId AS uuid) IS NULL
                    OR cl.account_manager_id = CAST(:managerId AS uuid))
            """,
            nativeQuery = true)
    Page<BillingRow> search(String status, String clientId, String contractId, LocalDate dueFrom,
                            LocalDate dueUntil, String managerId, Pageable pageable);

    /**
     * Projeção da listagem.
     *
     * <p>Interface e não classe: o Spring Data implementa sozinho a partir dos
     * nomes das colunas, e nenhum construtor precisa ser mantido em sincronia
     * com o {@code SELECT}.
     */
    interface BillingRow {
        UUID getId();

        UUID getClientId();

        UUID getContractId();

        String getReference();

        Short getInstallment();

        Short getTotalInstallments();

        BigDecimal getAmount();

        BigDecimal getTotalPago();

        String getCurrency();

        LocalDate getDueDate();

        String getStatus();
    }
}
