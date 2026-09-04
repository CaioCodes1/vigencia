package com.caiocodes.crbap.billing.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Modelo de persistência da cobrança. */
@Entity
@Table(name = "billings")
@Getter
@Setter
@NoArgsConstructor
public class BillingEntity {

    @Id
    private UUID id;

    /** Nulo em cobrança avulsa. Referência por id, como em contratos. */
    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 80)
    private String reference;

    @Column
    private Short installment;

    @Column(name = "total_installments")
    private Short totalInstallments;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingStatusValue status;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(length = 2000)
    private String notes;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /**
     * Cascade + orphanRemoval porque o pagamento só existe dentro da cobrança.
     * O {@code orphanRemoval} aqui é teoria: pagamento nunca sai da lista — é
     * estornado, não removido. Fica pela coerência com a fronteira do agregado.
     */
    @OneToMany(mappedBy = "billing", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PaymentEntity> payments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@code Long}, não {@code long} — ver a nota no {@code UserEntity}. */
    @Version
    private Long version;

    /** Espelha o CHECK da migração; o enum de domínio mora em billing.domain. */
    public enum BillingStatusValue {
        PENDING, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED
    }
}
