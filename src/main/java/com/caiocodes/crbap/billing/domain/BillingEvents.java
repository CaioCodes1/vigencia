package com.caiocodes.crbap.billing.domain;

import com.caiocodes.crbap.shared.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os fatos que a cobrança publica.
 *
 * <p>Mesmas regras dos eventos de contrato: só tipos primitivos no corpo, porque
 * o evento é a fronteira com quem consome, e o carimbo de tempo é metadado — a
 * data que <b>importa</b> para o negócio (vencimento, data do pagamento) vai
 * como campo próprio.
 */
public final class BillingEvents {

    private BillingEvents() {
    }

    static UUID nextEventId() {
        return UUID.randomUUID();
    }

    static Instant nowForMetadata() {
        return Instant.now();
    }

    /** Emitida. Na fase 6 é o gatilho do e-mail com o boleto. */
    public record BillingIssued(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, UUID contractId, String reference,
            BigDecimal amount, String currency, LocalDate dueDate)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Billing";
        }

        @Override
        public String eventType() {
            return "billing.issued";
        }
    }

    /**
     * Dinheiro entrou. {@code settled} diz se essa entrada quitou a cobrança —
     * quem consome precisa distinguir "recebemos algo" de "está pago".
     */
    public record PaymentRegistered(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID paymentId, UUID clientId, UUID contractId, String reference,
            BigDecimal amount, String currency, String method,
            BigDecimal totalPaid, BigDecimal remaining, boolean settled)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Billing";
        }

        @Override
        public String eventType() {
            return "billing.payment_registered";
        }
    }

    public record PaymentRefunded(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID paymentId, UUID clientId, BigDecimal amount, String currency, String reason)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Billing";
        }

        @Override
        public String eventType() {
            return "billing.payment_refunded";
        }
    }

    /** Passou do vencimento. É o que dispara a régua de cobrança na fase 6. */
    public record BillingOverdue(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, UUID contractId, String reference,
            BigDecimal remaining, String currency, LocalDate dueDate, long daysLate)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Billing";
        }

        @Override
        public String eventType() {
            return "billing.overdue";
        }
    }

    public record BillingCancelled(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, UUID contractId, String reference, String reason)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Billing";
        }

        @Override
        public String eventType() {
            return "billing.cancelled";
        }
    }
}
