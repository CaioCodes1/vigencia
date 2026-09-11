package com.caiocodes.vigencia.contract.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Modelo de persistência do contrato.
 *
 * <p>Sem {@code @ManyToOne} para cliente nem para o contrato anterior: as
 * referências são {@code UUID} puro. Um {@code @ManyToOne ClientEntity} faria
 * cada listagem de contratos arrastar o cliente junto (e, com ele, o documento
 * cifrado), e o link entre agregados deixaria de ser explícito. A integridade
 * continua garantida — pelas FKs da migração, que é onde ela pertence.
 */
@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class ContractEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "value_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal valueAmount;

    @Column(name = "value_currency", nullable = false, length = 3)
    private String valueCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycleValue billingCycle;

    @Column(name = "billing_day")
    private Short billingDay;

    @Column(name = "grace_period_days", nullable = false)
    private short gracePeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatusValue status;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "previous_contract_id")
    private UUID previousContractId;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    /**
     * Chave da requisição que criou este contrato por renovação. Nulo em
     * contrato criado normalmente — e o índice único é parcial justamente por
     * isso, já que NULL não conflita com NULL.
     */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@code Long}, não {@code long} — ver a nota no {@code UserEntity}. */
    @Version
    private Long version;

    /** Espelham os CHECKs da migração; os enums de domínio moram em contract.domain. */
    public enum ContractStatusValue {
        DRAFT, ACTIVE, SUSPENDED, RENEWED, EXPIRED, CANCELLED
    }

    public enum BillingCycleValue {
        MONTHLY, QUARTERLY, SEMIANNUAL, YEARLY, ONE_TIME
    }
}
