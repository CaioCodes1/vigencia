package com.caiocodes.vigencia.billing.application;

import com.caiocodes.vigencia.billing.domain.BillingId;
import com.caiocodes.vigencia.billing.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Entradas dos casos de uso de cobrança. */
public final class BillingCommands {

    private BillingCommands() {
    }

    /**
     * Cobrança avulsa: multa, serviço extra, acordo. Não tem parcela nem
     * contrato — quem tem contrato é gerado na ativação, não pela API.
     */
    public record CreateBilling(
            UUID clientId,
            UUID contractId,
            String reference,
            BigDecimal amount,
            String currency,
            LocalDate dueDate,
            String notes,
            String idempotencyKey) {
    }

    /** @param paidAt quando o dinheiro entrou, que pode não ser agora */
    public record RegisterPayment(
            BillingId billingId,
            BigDecimal amount,
            String currency,
            PaymentMethod method,
            Instant paidAt,
            String externalId,
            String idempotencyKey) {
    }

    public record RefundPayment(UUID paymentId, String reason) {
    }

    public record CancelBilling(BillingId billingId, String reason) {
    }
}
