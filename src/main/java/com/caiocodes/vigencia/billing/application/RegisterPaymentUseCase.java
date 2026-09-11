package com.caiocodes.vigencia.billing.application;

import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.billing.domain.Billing;
import com.caiocodes.vigencia.billing.domain.BillingRepository;
import com.caiocodes.vigencia.billing.domain.Payment;
import com.caiocodes.vigencia.shared.application.CurrentUser;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import com.caiocodes.vigencia.shared.domain.Money;
import com.caiocodes.vigencia.shared.domain.exception.CurrencyMismatchException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra um pagamento, total ou parcial (RF-17).
 *
 * <p>É o caso de uso onde dinheiro entra, e por isso tem as mesmas três defesas
 * da renovação, pelas mesmas razões:
 *
 * <ol>
 *   <li><b>{@code Idempotency-Key} obrigatória.</b> Diferente da renovação, aqui
 *       ela não é opcional: o gateway de pagamento reenvia webhook, e registrar
 *       o mesmo PIX duas vezes é dinheiro contabilizado em dobro. Repetir a
 *       chave devolve <b>200</b> com o mesmo pagamento.</li>
 *   <li><b>{@code SELECT ... FOR UPDATE} na cobrança.</b> Dois pagamentos
 *       simultâneos leriam o mesmo saldo e os dois passariam pela checagem de
 *       "excede o saldo devedor".</li>
 *   <li><b>{@code uk_payments_idem}</b>, único e <i>não</i> parcial, como rede
 *       final no banco.</li>
 * </ol>
 *
 * <p>A moeda tem que bater com a da cobrança. Quem garante é o próprio
 * {@code Money}, que estoura {@code CurrencyMismatchException} ao somar — vira
 * 422, e não um número sem significado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPaymentUseCase {

    private final BillingRepository billings;
    private final BillingFinder finder;
    private final CurrentUser currentUser;
    private final DomainEventRecorder events;
    private final Clock clock;

    /**
     * @param repeated true quando a mesma chave já tinha registrado — é o que
     *                 faz o controller responder 200 em vez de 201
     */
    public record PaymentResult(BillingDetail billing, UUID paymentId, boolean repeated) {
    }

    @Transactional
    @Auditable(entity = "Billing", action = "PAY", id = "#result.billing().id()")
    public PaymentResult execute(BillingCommands.RegisterPayment command) {
        LocalDate today = LocalDate.now(clock);

        Optional<Billing> jaRegistrado = alreadyPaid(command.idempotencyKey());
        if (jaRegistrado.isPresent()) {
            Billing existente = jaRegistrado.get();
            Payment pagamento = existente.payments().stream()
                    .filter(p -> command.idempotencyKey().equals(p.idempotencyKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Cobrança encontrada pela chave mas sem o pagamento: "
                                    + command.idempotencyKey()));
            log.info("billing.payment.repeated billingId={} key={}",
                    existente.id(), command.idempotencyKey());
            return new PaymentResult(
                    BillingDetail.from(existente, finder.clientNameOf(existente), today),
                    pagamento.id(), true);
        }

        Billing billing = finder.requireForUpdate(command.billingId());
        Money valor = CreateBillingUseCase.money(command.amount(), command.currency());
        requireSameCurrency(billing, valor);

        Payment pagamento = billing.registerPayment(valor, command.method(), command.paidAt(),
                command.externalId(), command.idempotencyKey(), currentUser.id());

        Billing saved = billings.save(billing);
        events.record(saved);
        log.info("billing.payment_registered billingId={} paymentId={} valor={} saldo={}",
                saved.id(), pagamento.id(), valor, saved.remaining());
        return new PaymentResult(
                BillingDetail.from(saved, finder.clientNameOf(saved), today),
                pagamento.id(), false);
    }

    private Optional<Billing> alreadyPaid(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return billings.findByPaymentIdempotencyKey(idempotencyKey);
    }

    /**
     * Checagem explícita para dar 422 com mensagem clara.
     *
     * <p>Sem ela o erro sairia igual — o {@code Money.add} recusaria ao somar —
     * mas só depois de o agregado já ter sido tocado, e vindo de um lugar onde
     * a mensagem fala de aritmética em vez de falar da requisição.
     */
    private void requireSameCurrency(Billing billing, Money valor) {
        if (!billing.amount().currency().equals(valor.currency())) {
            throw new CurrencyMismatchException(
                    billing.amount().currency().getCurrencyCode(),
                    valor.currency().getCurrencyCode());
        }
    }
}
