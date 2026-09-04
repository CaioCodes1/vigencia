package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.billing.domain.Billing;
import com.caiocodes.crbap.billing.domain.BillingStatus;
import com.caiocodes.crbap.billing.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Visão da cobrança para fora do domínio, com os pagamentos.
 *
 * <p>{@code totalPaid}, {@code remaining} e {@code daysLate} são calculados na
 * montagem, a partir da data de referência que o caso de uso passa. Nenhum deles
 * é coluna — ver o Javadoc de {@code Billing}.
 */
public record BillingDetail(
        UUID id,
        UUID clientId,
        String clientName,
        UUID contractId,
        String reference,
        Integer installment,
        Integer totalInstallments,
        BigDecimal amount,
        BigDecimal totalPaid,
        BigDecimal remaining,
        String currency,
        LocalDate dueDate,
        LocalDate issueDate,
        long daysLate,
        BillingStatus status,
        String notes,
        String cancellationReason,
        List<PaymentView> payments) {

    public record PaymentView(UUID id, BigDecimal amount, String currency, String method,
                              Instant paidAt, String externalId, boolean refunded,
                              Instant refundedAt, String refundReason) {
    }

    public static BillingDetail from(Billing billing, String clientName, LocalDate today) {
        return new BillingDetail(
                billing.id().value(),
                billing.clientId(),
                clientName,
                billing.contractId(),
                billing.reference(),
                billing.installment(),
                billing.totalInstallments(),
                billing.amount().amount(),
                billing.totalPaid().amount(),
                billing.remaining().amount(),
                billing.amount().currency().getCurrencyCode(),
                billing.dueDate(),
                billing.issueDate(),
                billing.daysLate(today),
                billing.status(),
                billing.notes(),
                billing.cancellationReason(),
                billing.payments().stream().map(BillingDetail::toView).toList());
    }

    private static PaymentView toView(Payment payment) {
        return new PaymentView(payment.id(), payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(), payment.method().name(),
                payment.paidAt(), payment.externalId(), payment.isRefunded(),
                payment.refundedAt(), payment.refundReason());
    }
}
