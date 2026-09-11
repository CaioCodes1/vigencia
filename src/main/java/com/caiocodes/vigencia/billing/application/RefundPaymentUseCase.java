package com.caiocodes.vigencia.billing.application;

import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.billing.domain.Billing;
import com.caiocodes.vigencia.billing.domain.BillingId;
import com.caiocodes.vigencia.billing.domain.BillingRepository;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estorno (RF-18).
 *
 * <p>Não apaga a linha: marca {@code refunded}, o valor deixa de contar no saldo
 * e a cobrança volta ao estado que o saldo mandar — inclusive {@code OVERDUE},
 * se o vencimento já passou. Apagar o pagamento significaria que dinheiro entrou
 * e sumiu do histórico, e a conciliação do mês passado deixaria de fechar sem
 * que ninguém consiga dizer por quê.
 *
 * <p>Só {@code payment:refund} chega aqui (FINANCE e ADMIN). É a operação que
 * mais se parece com "consertar" e a que mais precisa de trilha.
 *
 * <p><b>Recebe o id do pagamento, não o da cobrança</b> — é o que o usuário tem
 * na mão ao olhar um extrato. Quem descobre a cobrança é o repositório, e o
 * escopo de carteira é conferido sobre ela.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundPaymentUseCase {

    private final BillingRepository billings;
    private final BillingFinder finder;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "Billing", action = "REFUND")
    public BillingDetail execute(BillingCommands.RefundPayment command) {
        LocalDate today = LocalDate.now(clock);

        BillingId billingId = billings.findByPaymentId(command.paymentId())
                .orElseThrow(() -> new NotFoundException("Pagamento", command.paymentId()));

        Billing billing = finder.requireForUpdate(billingId);
        billing.refundPayment(command.paymentId(), command.reason(), clock.instant(), today);

        Billing saved = billings.save(billing);
        events.record(saved);
        log.info("billing.payment_refunded billingId={} paymentId={} status={} saldo={}",
                saved.id(), command.paymentId(), saved.status(), saved.remaining());
        return BillingDetail.from(saved, finder.clientNameOf(saved), today);
    }

    /** Só para o controller montar a URI de retorno sem recarregar. */
    public UUID billingIdOf(UUID paymentId) {
        return billings.findByPaymentId(paymentId)
                .map(BillingId::value)
                .orElseThrow(() -> new NotFoundException("Pagamento", paymentId));
    }
}
