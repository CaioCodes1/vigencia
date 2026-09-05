package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.audit.application.Auditable;
import com.caiocodes.crbap.billing.domain.Billing;
import com.caiocodes.crbap.billing.domain.BillingRepository;
import com.caiocodes.crbap.billing.domain.NewBilling;
import com.caiocodes.crbap.shared.application.ClientDirectory;
import com.caiocodes.crbap.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.crbap.shared.application.CurrentUser;
import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import com.caiocodes.crbap.shared.domain.Money;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import com.caiocodes.crbap.shared.domain.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobrança avulsa (RF-16): multa, serviço extra, acordo.
 *
 * <p>Não é por aqui que nasce a parcela de um contrato — essa sai da ativação,
 * na mesma transação (ADR-006). Se a API pudesse criar parcelas de contrato à
 * mão, o total cobrado deixaria de bater com o valor contratado e ninguém
 * conseguiria dizer qual dos dois está certo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateBillingUseCase {

    private static final String DEFAULT_CURRENCY = "BRL";

    private final BillingRepository billings;
    private final ClientDirectory clients;
    private final CurrentUser currentUser;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "Billing", action = "CREATE")
    public BillingDetail execute(BillingCommands.CreateBilling command) {
        LocalDate today = LocalDate.now(clock);
        ClientRef client = requireClientInScope(command.clientId());

        if (!client.active()) {
            throw new BusinessRuleException("CLIENT_NOT_ACTIVE",
                    "Só é possível cobrar cliente ativo");
        }

        if (command.idempotencyKey() != null) {
            var existente = billings.findByIdempotencyKey(command.idempotencyKey());
            if (existente.isPresent()) {
                return BillingDetail.from(existente.get(), client.legalName(), today);
            }
        }

        Billing billing = Billing.issue(new NewBilling(
                client.id(),
                command.contractId(),
                command.reference(),
                null,
                null,
                money(command.amount(), command.currency()),
                command.dueDate(),
                today,
                command.notes(),
                command.idempotencyKey()));

        Billing saved = billings.save(billing);
        events.record(saved);
        log.info("billing.issued billingId={} clientId={} valor={}",
                saved.id(), saved.clientId(), saved.amount());
        return BillingDetail.from(saved, client.legalName(), today);
    }

    private ClientRef requireClientInScope(UUID clientId) {
        ClientRef client = clients.findRef(clientId)
                .orElseThrow(() -> new NotFoundException("Cliente", clientId));
        UUID scope = currentUser.scopeOrNull(BillingFinder.READ_ALL);
        if (scope != null && !scope.equals(client.accountManagerId())) {
            throw new NotFoundException("Cliente", clientId);
        }
        return client;
    }

    static Money money(BigDecimal amount, String currency) {
        String code = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency;
        return Money.of(amount, Currency.getInstance(code));
    }
}
