package com.caiocodes.vigencia.billing.domain;

import com.caiocodes.vigencia.shared.domain.Money;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um pagamento recebido, dentro do agregado {@link Billing}.
 *
 * <p>Não é raiz de agregado: um pagamento sem cobrança não significa nada, e
 * toda alteração nele muda o saldo da cobrança. Por isso ele só é criado e
 * estornado <b>através</b> da cobrança — quem chama nunca segura um
 * {@code Payment} solto e mexe nele.
 *
 * <p><b>Estornar não apaga.</b> Marca {@code refunded} e o valor deixa de contar
 * no saldo. Apagar a linha significaria que dinheiro entrou e sumiu do
 * histórico, e a conciliação bancária do mês passado deixaria de fechar sem que
 * ninguém consiga dizer por quê.
 */
public class Payment {

    private static final int MIN_REASON = 10;
    private static final int MAX_REASON = 500;

    private final UUID id;
    private final Money amount;
    private final PaymentMethod method;
    private final Instant paidAt;
    private final String externalId;
    private final String idempotencyKey;
    private final UUID registeredBy;
    private boolean refunded;
    private Instant refundedAt;
    private String refundReason;

    private Payment(UUID id, Money amount, PaymentMethod method, Instant paidAt,
                    String externalId, String idempotencyKey, UUID registeredBy) {
        this.id = Objects.requireNonNull(id, "o id do pagamento é obrigatório");
        this.amount = requirePositive(amount);
        this.method = Objects.requireNonNull(method, "o meio de pagamento é obrigatório");
        this.paidAt = Objects.requireNonNull(paidAt, "a data do pagamento é obrigatória");
        this.externalId = blankToNull(externalId);
        this.idempotencyKey = requireKey(idempotencyKey);
        this.registeredBy = registeredBy;
    }

    static Payment of(Money amount, PaymentMethod method, Instant paidAt, String externalId,
                      String idempotencyKey, UUID registeredBy) {
        return new Payment(UUID.randomUUID(), amount, method, paidAt, externalId,
                idempotencyKey, registeredBy);
    }

    /** Só a camada de persistência deve chamar. */
    public static Payment rehydrate(UUID id, Money amount, PaymentMethod method, Instant paidAt,
                                    String externalId, String idempotencyKey, UUID registeredBy,
                                    boolean refunded, Instant refundedAt, String refundReason) {
        Payment payment = new Payment(id, amount, method, paidAt, externalId, idempotencyKey,
                registeredBy);
        payment.refunded = refunded;
        payment.refundedAt = refundedAt;
        payment.refundReason = refundReason;
        return payment;
    }

    void refund(String reason, Instant now) {
        if (refunded) {
            throw new BusinessRuleException("PAYMENT_ALREADY_REFUNDED",
                    "Este pagamento já foi estornado");
        }
        this.refundReason = requireReason(reason);
        this.refunded = true;
        this.refundedAt = now;
    }

    /** Conta para o saldo? Estornado, não conta. */
    public boolean isEffective() {
        return !refunded;
    }

    public UUID id() {
        return id;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethod method() {
        return method;
    }

    public Instant paidAt() {
        return paidAt;
    }

    public String externalId() {
        return externalId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public UUID registeredBy() {
        return registeredBy;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public Instant refundedAt() {
        return refundedAt;
    }

    public String refundReason() {
        return refundReason;
    }

    private static Money requirePositive(Money amount) {
        Objects.requireNonNull(amount, "o valor do pagamento é obrigatório");
        if (!amount.isPositive()) {
            throw new BusinessRuleException("O valor pago deve ser maior que zero");
        }
        return amount;
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            // Diferente da renovação, aqui a chave NÃO é opcional: registrar o
            // mesmo pagamento duas vezes é dinheiro contabilizado em dobro.
            throw new BusinessRuleException("MISSING_IDEMPOTENCY_KEY",
                    "Todo pagamento precisa de uma chave de idempotência");
        }
        return key.strip();
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.length() < MIN_REASON || normalized.length() > MAX_REASON) {
            throw new BusinessRuleException("MISSING_REASON",
                    "Informe um motivo de estorno entre " + MIN_REASON + " e " + MAX_REASON
                            + " caracteres");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
