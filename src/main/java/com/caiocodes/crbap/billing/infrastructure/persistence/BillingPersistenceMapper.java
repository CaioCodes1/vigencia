package com.caiocodes.crbap.billing.infrastructure.persistence;

import com.caiocodes.crbap.billing.domain.Billing;
import com.caiocodes.crbap.billing.domain.BillingId;
import com.caiocodes.crbap.billing.domain.BillingListItem;
import com.caiocodes.crbap.billing.domain.BillingState;
import com.caiocodes.crbap.billing.domain.BillingStatus;
import com.caiocodes.crbap.billing.domain.Payment;
import com.caiocodes.crbap.billing.domain.PaymentMethod;
import com.caiocodes.crbap.billing.infrastructure.persistence.BillingEntity.BillingStatusValue;
import com.caiocodes.crbap.billing.infrastructure.persistence.BillingJpaRepository.BillingRow;
import com.caiocodes.crbap.billing.infrastructure.persistence.PaymentEntity.PaymentMethodValue;
import com.caiocodes.crbap.shared.domain.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tradução entre o agregado Cobrança e o modelo de persistência. */
@Component
public class BillingPersistenceMapper {

    public Billing toDomain(BillingEntity entity) {
        Currency moeda = Currency.getInstance(entity.getCurrency());

        List<Payment> pagamentos = entity.getPayments().stream()
                .map(p -> Payment.rehydrate(p.getId(),
                        Money.of(p.getAmount(), Currency.getInstance(p.getCurrency())),
                        PaymentMethod.valueOf(p.getMethod().name()), p.getPaidAt(),
                        p.getExternalId(), p.getIdempotencyKey(), p.getRegisteredBy(),
                        p.isRefunded(), p.getRefundedAt(), p.getRefundReason()))
                .toList();

        return Billing.rehydrate(new BillingState(
                BillingId.of(entity.getId()),
                entity.getClientId(),
                entity.getContractId(),
                entity.getReference(),
                entity.getInstallment() == null ? null : entity.getInstallment().intValue(),
                entity.getTotalInstallments() == null ? null
                        : entity.getTotalInstallments().intValue(),
                Money.of(entity.getAmount(), moeda),
                entity.getDueDate(),
                entity.getIssueDate(),
                BillingStatus.valueOf(entity.getStatus().name()),
                entity.getNotes(),
                entity.getCancellationReason(),
                entity.getIdempotencyKey(),
                pagamentos,
                entity.getVersion() == null ? 0L : entity.getVersion()));
    }

    /** Vem da projeção nativa: o total pago já foi somado pelo banco. */
    public BillingListItem toListItem(BillingRow row) {
        Currency moeda = Currency.getInstance(row.getCurrency());
        BigDecimal pago = row.getTotalPago() == null ? BigDecimal.ZERO : row.getTotalPago();

        return new BillingListItem(
                BillingId.of(row.getId()),
                row.getClientId(),
                row.getContractId(),
                row.getReference(),
                row.getInstallment() == null ? null : row.getInstallment().intValue(),
                row.getTotalInstallments() == null ? null : row.getTotalInstallments().intValue(),
                Money.of(row.getAmount(), moeda),
                Money.of(pago.setScale(2), moeda),
                row.getDueDate(),
                BillingStatus.valueOf(row.getStatus()));
    }

    public void copyToEntity(Billing billing, BillingEntity entity) {
        entity.setId(billing.id().value());
        entity.setClientId(billing.clientId());
        entity.setContractId(billing.contractId());
        entity.setReference(billing.reference());
        entity.setInstallment(billing.installment() == null ? null
                : billing.installment().shortValue());
        entity.setTotalInstallments(billing.totalInstallments() == null ? null
                : billing.totalInstallments().shortValue());
        entity.setAmount(billing.amount().amount());
        entity.setCurrency(billing.amount().currency().getCurrencyCode());
        entity.setDueDate(billing.dueDate());
        entity.setIssueDate(billing.issueDate());
        entity.setStatus(BillingStatusValue.valueOf(billing.status().name()));
        entity.setIdempotencyKey(billing.idempotencyKey());
        entity.setNotes(billing.notes());
        entity.setCancellationReason(billing.cancellationReason());
        syncPayments(billing, entity);
    }

    /**
     * Sincroniza sem apagar e recriar.
     *
     * <p>Aqui é ainda mais crítico que em contatos de cliente: recriar um
     * pagamento a cada gravação daria a ele um id novo, e o id do pagamento é o
     * que o financeiro usa para estornar. Pagamento existente só é
     * <b>atualizado</b> — e o único campo que muda é o estorno.
     */
    private void syncPayments(Billing billing, BillingEntity entity) {
        Map<UUID, PaymentEntity> existentes = new HashMap<>();
        entity.getPayments().forEach(p -> existentes.put(p.getId(), p));

        for (Payment payment : billing.payments()) {
            PaymentEntity alvo = existentes.get(payment.id());
            if (alvo == null) {
                alvo = new PaymentEntity();
                alvo.setId(payment.id());
                alvo.setBilling(entity);
                alvo.setAmount(payment.amount().amount());
                alvo.setCurrency(payment.amount().currency().getCurrencyCode());
                alvo.setMethod(PaymentMethodValue.valueOf(payment.method().name()));
                alvo.setPaidAt(payment.paidAt());
                alvo.setExternalId(payment.externalId());
                alvo.setIdempotencyKey(payment.idempotencyKey());
                alvo.setRegisteredBy(payment.registeredBy());
                entity.getPayments().add(alvo);
            }
            alvo.setRefunded(payment.isRefunded());
            alvo.setRefundedAt(payment.refundedAt());
            alvo.setRefundReason(payment.refundReason());
        }
    }
}
