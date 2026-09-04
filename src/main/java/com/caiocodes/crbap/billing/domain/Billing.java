package com.caiocodes.crbap.billing.domain;

import com.caiocodes.crbap.billing.domain.BillingEvents.BillingCancelled;
import com.caiocodes.crbap.billing.domain.BillingEvents.BillingIssued;
import com.caiocodes.crbap.billing.domain.BillingEvents.BillingOverdue;
import com.caiocodes.crbap.billing.domain.BillingEvents.PaymentRefunded;
import com.caiocodes.crbap.billing.domain.BillingEvents.PaymentRegistered;
import com.caiocodes.crbap.shared.domain.AggregateRoot;
import com.caiocodes.crbap.shared.domain.Money;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cobrança: dinheiro a receber, com pagamento parcial.
 *
 * <p>O saldo <b>nunca</b> é um campo. É sempre {@code amount - soma dos
 * pagamentos efetivos}, calculado na hora. Guardar o saldo criaria duas fontes
 * de verdade para a mesma informação, e a primeira vez que um estorno esquecesse
 * de atualizar o campo, a empresa cobraria alguém que já pagou.
 *
 * <p>Pela mesma razão o {@code status} é <b>derivado</b> do saldo em todo método
 * que mexe em dinheiro, em vez de ser atribuído em cada caminho: registrar,
 * estornar e cancelar chamam a mesma função, então não existe caminho que
 * esqueça de recalcular.
 */
public class Billing extends AggregateRoot<BillingId> {

    private static final int MIN_REASON = 10;
    private static final int MAX_REASON = 500;

    private final BillingId id;
    private final UUID clientId;
    private final UUID contractId;
    private final String reference;
    private final Integer installment;
    private final Integer totalInstallments;
    private final Money amount;
    private final LocalDate dueDate;
    private final LocalDate issueDate;
    private BillingStatus status;
    private String notes;
    private String cancellationReason;
    private final String idempotencyKey;
    private final List<Payment> payments = new ArrayList<>();
    private long version;

    private Billing(BillingId id, UUID clientId, UUID contractId, String reference,
                    Integer installment, Integer totalInstallments, Money amount,
                    LocalDate dueDate, LocalDate issueDate, String idempotencyKey) {
        this.id = Objects.requireNonNull(id, "o id é obrigatório");
        this.clientId = Objects.requireNonNull(clientId, "o cliente é obrigatório");
        this.contractId = contractId;
        this.reference = requireReference(reference);
        this.installment = installment;
        this.totalInstallments = totalInstallments;
        this.amount = requirePositive(amount);
        this.dueDate = Objects.requireNonNull(dueDate, "o vencimento é obrigatório");
        this.issueDate = Objects.requireNonNull(issueDate, "a data de emissão é obrigatória");
        this.idempotencyKey = blankToNull(idempotencyKey);
        this.status = BillingStatus.PENDING;
        requireInstallmentConsistency();
    }

    /** Fábrica: toda cobrança nasce PENDING. */
    public static Billing issue(NewBilling data) {
        Billing billing = new Billing(BillingId.newId(), data.clientId(), data.contractId(),
                data.reference(), data.installment(), data.totalInstallments(), data.amount(),
                data.dueDate(), data.issueDate(), data.idempotencyKey());
        billing.notes = blankToNull(data.notes());
        billing.register(new BillingIssued(BillingEvents.nextEventId(),
                BillingEvents.nowForMetadata(), billing.id.value(), data.clientId(),
                data.contractId(), billing.reference, data.amount().amount(),
                data.amount().currency().getCurrencyCode(), data.dueDate()));
        return billing;
    }

    /** Só a camada de persistência deve chamar. */
    public static Billing rehydrate(BillingState state) {
        Billing billing = new Billing(state.id(), state.clientId(), state.contractId(),
                state.reference(), state.installment(), state.totalInstallments(),
                state.amount(), state.dueDate(), state.issueDate(), state.idempotencyKey());
        billing.status = state.status();
        billing.notes = state.notes();
        billing.cancellationReason = state.cancellationReason();
        billing.payments.addAll(state.payments());
        billing.version = state.version();
        return billing;
    }

    // =================================================================
    // Dinheiro
    // =================================================================

    /**
     * Registra um pagamento, total ou parcial.
     *
     * <p>Recusa o que passa do saldo em vez de aceitar e gerar crédito: crédito
     * a favor do cliente é outro assunto do negócio, com outras regras (para
     * onde vai, quando expira, quem autoriza). Aceitar aqui em silêncio criaria
     * um passivo que nenhum relatório mostra.
     */
    public Payment registerPayment(Money paid, PaymentMethod method, Instant paidAt,
                                   String externalId, String idempotencyKeyOfPayment,
                                   UUID registeredBy) {
        if (status == BillingStatus.CANCELLED) {
            throw new BusinessRuleException("BILLING_CANCELLED",
                    "Cobrança cancelada não recebe pagamento");
        }
        Money depois = totalPaid().add(paid);
        if (depois.isGreaterThan(amount)) {
            throw new BusinessRuleException("PAYMENT_EXCEEDS_BALANCE",
                    "Pagamento de " + paid + " excede o saldo devedor de " + remaining());
        }

        Payment payment = Payment.of(paid, method, paidAt, externalId, idempotencyKeyOfPayment,
                registeredBy);
        payments.add(payment);
        recalculateStatus();

        register(new PaymentRegistered(BillingEvents.nextEventId(),
                BillingEvents.nowForMetadata(), id.value(), payment.id(), clientId, contractId,
                paid.amount(), paid.currency().getCurrencyCode(), method.name(),
                totalPaid().amount(), remaining().amount(), status == BillingStatus.PAID));
        return payment;
    }

    /**
     * Estorna um pagamento e devolve a cobrança ao estado que o saldo mandar.
     *
     * <p>Uma cobrança já quitada volta a ficar em aberto — inclusive
     * {@code OVERDUE}, se o vencimento já passou. É o comportamento certo: o
     * dinheiro não está mais lá.
     */
    public Payment refundPayment(UUID paymentId, String reason, Instant now, LocalDate today) {
        Payment payment = payments.stream()
                .filter(p -> p.id().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND",
                        "Pagamento não encontrado nesta cobrança"));

        payment.refund(reason, now);
        recalculateStatus();
        if (status.isOpen() && today.isAfter(dueDate)) {
            status = BillingStatus.OVERDUE;
        }

        register(new PaymentRefunded(BillingEvents.nextEventId(),
                BillingEvents.nowForMetadata(), id.value(), payment.id(), clientId,
                payment.amount().amount(), payment.amount().currency().getCurrencyCode(),
                payment.refundReason()));
        return payment;
    }

    // =================================================================
    // Ciclo de vida
    // =================================================================

    /**
     * Marca como vencida — chamada em lote pelo job diário.
     *
     * <p><b>Retorna sem erro</b> quando não se aplica, em vez de lançar. É
     * chamada sobre milhares de cobranças e "essa aqui não se aplica" é o caso
     * normal, não um erro. Já {@code registerPayment} lança, porque ali existe
     * um humano do outro lado que precisa saber o que deu errado.
     *
     * @return true se mudou de estado
     */
    public boolean markOverdue(LocalDate today) {
        if (status != BillingStatus.PENDING && status != BillingStatus.PARTIALLY_PAID) {
            return false;
        }
        if (!today.isAfter(dueDate)) {
            return false;
        }
        this.status = BillingStatus.OVERDUE;
        register(new BillingOverdue(BillingEvents.nextEventId(),
                BillingEvents.nowForMetadata(), id.value(), clientId, contractId, reference,
                remaining().amount(), amount.currency().getCurrencyCode(), dueDate,
                daysLate(today)));
        return true;
    }

    /**
     * Cancela a cobrança.
     *
     * <p>Cobrança que já recebeu dinheiro <b>não</b> é cancelável: o caminho
     * para desfazer aquilo é o estorno, que deixa rastro de para onde o dinheiro
     * voltou. Cancelar por cima esconderia um pagamento recebido.
     */
    public void cancel(String reason) {
        if (status == BillingStatus.CANCELLED) {
            throw new BusinessRuleException("BILLING_ALREADY_CANCELLED",
                    "Esta cobrança já está cancelada");
        }
        if (!totalPaid().isZero()) {
            throw new BusinessRuleException("BILLING_HAS_PAYMENTS",
                    "Cobrança com pagamento registrado não pode ser cancelada: estorne primeiro");
        }
        this.cancellationReason = requireReason(reason);
        this.status = BillingStatus.CANCELLED;
        register(new BillingCancelled(BillingEvents.nextEventId(),
                BillingEvents.nowForMetadata(), id.value(), clientId, contractId, reference,
                cancellationReason));
    }

    // =================================================================
    // Cálculos — sempre derivados, nunca guardados
    // =================================================================

    public Money totalPaid() {
        return payments.stream()
                .filter(Payment::isEffective)
                .map(Payment::amount)
                .reduce(Money.zero(amount.currency()), Money::add);
    }

    public Money remaining() {
        return amount.subtract(totalPaid());
    }

    public long daysLate(LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
    }

    public boolean isSettled() {
        return status == BillingStatus.PAID;
    }

    public Optional<Payment> paymentById(UUID paymentId) {
        return payments.stream().filter(p -> p.id().equals(paymentId)).findFirst();
    }

    /**
     * O status sai do saldo, em um lugar só.
     *
     * <p>Atribuir o status dentro de cada método que mexe em pagamento
     * funcionaria — até alguém acrescentar o quarto caminho e esquecer.
     */
    private void recalculateStatus() {
        Money pago = totalPaid();
        if (pago.isZero()) {
            this.status = BillingStatus.PENDING;
        } else if (pago.compareTo(amount) >= 0) {
            this.status = BillingStatus.PAID;
        } else {
            this.status = BillingStatus.PARTIALLY_PAID;
        }
    }

    // =================================================================
    // Acessores
    // =================================================================

    @Override
    public BillingId id() {
        return id;
    }

    public UUID clientId() {
        return clientId;
    }

    public UUID contractId() {
        return contractId;
    }

    public String reference() {
        return reference;
    }

    public Integer installment() {
        return installment;
    }

    public Integer totalInstallments() {
        return totalInstallments;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public LocalDate issueDate() {
        return issueDate;
    }

    public BillingStatus status() {
        return status;
    }

    public String notes() {
        return notes;
    }

    public String cancellationReason() {
        return cancellationReason;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public List<Payment> payments() {
        return Collections.unmodifiableList(payments);
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "Billing[" + reference + ", " + status + ", saldo " + remaining() + "]";
    }

    // =================================================================
    // Invariantes
    // =================================================================

    private void requireInstallmentConsistency() {
        if (installment == null && totalInstallments == null) {
            return;
        }
        if (installment == null || totalInstallments == null) {
            throw new BusinessRuleException(
                    "Parcela e total de parcelas andam juntos: informe os dois ou nenhum");
        }
        if (installment < 1 || installment > totalInstallments) {
            throw new BusinessRuleException(
                    "Parcela " + installment + " fora do total de " + totalInstallments);
        }
        if (contractId == null) {
            // Cobrança avulsa não é "1 de 12" — o CHECK da migração diz o mesmo.
            throw new BusinessRuleException("Cobrança avulsa não tem parcela");
        }
    }

    private static Money requirePositive(Money amount) {
        Objects.requireNonNull(amount, "o valor é obrigatório");
        if (!amount.isPositive()) {
            throw new BusinessRuleException("O valor da cobrança deve ser maior que zero");
        }
        return amount;
    }

    private static String requireReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessRuleException("A referência da cobrança é obrigatória");
        }
        return reference.strip();
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.length() < MIN_REASON || normalized.length() > MAX_REASON) {
            throw new BusinessRuleException("MISSING_REASON",
                    "Informe um motivo entre " + MIN_REASON + " e " + MAX_REASON + " caracteres");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
