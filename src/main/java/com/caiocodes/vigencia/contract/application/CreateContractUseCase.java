package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.contract.domain.Contract;
import com.caiocodes.vigencia.contract.domain.ContractNumberGenerator;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.contract.domain.NewContract;
import com.caiocodes.vigencia.shared.application.ClientDirectory;
import com.caiocodes.vigencia.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.vigencia.shared.application.CurrentUser;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import com.caiocodes.vigencia.shared.domain.DateRange;
import com.caiocodes.vigencia.shared.domain.Money;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria o contrato em DRAFT (RF-08). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateContractUseCase {

    static final String READ_ALL = "contract:read_all";
    private static final String DEFAULT_CURRENCY = "BRL";

    private final ContractRepository contracts;
    private final ClientDirectory clients;
    private final ContractNumberGenerator numbers;
    private final CurrentUser currentUser;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "Contract", action = "CREATE")
    public ContractDetail execute(ContractCommands.CreateContract command) {
        LocalDate today = LocalDate.now(clock);
        ClientRef client = requireClientInScope(command.clientId());

        if (!client.active()) {
            throw new BusinessRuleException("CLIENT_NOT_ACTIVE",
                    "Só é possível criar contrato para cliente ativo");
        }

        String number = command.number() == null || command.number().isBlank()
                ? numbers.next(today)
                : command.number().strip().toUpperCase();

        // Checagem amigável para devolver 409 com mensagem em vez de deixar o
        // uk_contracts_number estourar. A garantia continua sendo o índice:
        // duas requisições simultâneas passam as duas por aqui.
        if (contracts.existsByNumber(number)) {
            throw new BusinessRuleException("DUPLICATE_CONTRACT_NUMBER",
                    "Já existe um contrato com o número " + number);
        }

        Contract contract = Contract.create(new NewContract(
                client.id(),
                number,
                command.title(),
                command.description(),
                DateRange.of(command.startDate(), command.endDate()),
                money(command.amount(), command.currency()),
                command.billingCycle(),
                command.billingDay(),
                command.gracePeriodDays() == null ? 0 : command.gracePeriodDays(),
                command.autoRenew(),
                currentUser.id()));

        Contract saved = contracts.save(contract);
        events.record(saved);
        log.info("contract.created contractId={} number={} clientId={}",
                saved.id(), saved.number(), saved.clientId());
        return ContractDetail.from(saved, client.legalName(), today);
    }

    /**
     * Cliente fora da carteira responde 404, e não 403.
     *
     * <p>403 confirmaria que aquele id existe — o que basta para um vendedor
     * enumerar a base de clientes da empresa inteira, um id por vez.
     */
    private ClientRef requireClientInScope(UUID clientId) {
        ClientRef client = clients.findRef(clientId)
                .orElseThrow(() -> new NotFoundException("Cliente", clientId));
        UUID scope = currentUser.scopeOrNull(READ_ALL);
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
