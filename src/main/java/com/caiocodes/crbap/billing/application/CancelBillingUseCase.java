package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.billing.domain.Billing;
import com.caiocodes.crbap.billing.domain.BillingRepository;
import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancela uma cobrança em aberto.
 *
 * <p>Cobrança que já recebeu dinheiro é recusada pelo agregado: o caminho para
 * desfazer aquilo é o estorno, que deixa rastro de para onde o dinheiro voltou.
 * Cancelar por cima esconderia um pagamento recebido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelBillingUseCase {

    private final BillingRepository billings;
    private final BillingFinder finder;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    public BillingDetail execute(BillingCommands.CancelBilling command) {
        Billing billing = finder.requireForUpdate(command.billingId());
        billing.cancel(command.reason());

        Billing saved = billings.save(billing);
        events.record(saved);
        log.info("billing.cancelled billingId={} reference={}", saved.id(), saved.reference());
        return BillingDetail.from(saved, finder.clientNameOf(saved), LocalDate.now(clock));
    }
}
