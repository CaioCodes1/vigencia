package com.caiocodes.crbap.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Modelo de persistência do pagamento.
 *
 * <p>Aqui existe {@code @ManyToOne} para a cobrança — e não um {@code UUID} solto
 * como nas referências entre módulos. A diferença é que pagamento e cobrança são
 * o <b>mesmo agregado</b>: a navegação é interna, e é ela que faz o cascade
 * gravar os dois juntos.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_id", nullable = false)
    private BillingEntity billing;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodValue method;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private boolean refunded;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "registered_by")
    private UUID registeredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Espelha o CHECK da migração. */
    public enum PaymentMethodValue {
        PIX, BOLETO, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CASH, OTHER
    }
}
