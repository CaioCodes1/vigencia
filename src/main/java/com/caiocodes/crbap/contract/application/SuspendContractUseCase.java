package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.audit.application.Auditable;
import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractId;
import com.caiocodes.crbap.contract.domain.ContractRepository;
import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suspensão e retomada.
 *
 * <p>Os dois na mesma classe porque são a mesma decisão de negócio vista dos
 * dois lados — a mesma escolha do {@code DeactivateClientUseCase}.
 *
 * <p>Contrato suspenso continua existindo e continua vencendo: a suspensão para
 * a <i>cobrança</i>, não o relógio. Por isso a varredura diária ignora
 * {@code SUSPENDED} — um contrato suspenso que passa da data precisa de decisão
 * humana, não de expiração automática.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspendContractUseCase {

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "Contract", action = "SUSPEND")
    public ContractDetail suspend(ContractCommands.SuspendContract command) {
        Contract contract = finder.requireForUpdate(command.contractId());
        contract.suspend(command.reason());

        Contract saved = contracts.save(contract);
        events.record(saved);
        log.info("contract.suspended contractId={}", saved.id());
        return ContractDetail.from(saved, finder.clientNameOf(saved), LocalDate.now(clock));
    }

    @Transactional
    @Auditable(entity = "Contract", action = "RESUME")
    public ContractDetail resume(ContractId id) {
        Contract contract = finder.requireForUpdate(id);
        contract.resume();

        Contract saved = contracts.save(contract);
        events.record(saved);
        log.info("contract.resumed contractId={}", saved.id());
        return ContractDetail.from(saved, finder.clientNameOf(saved), LocalDate.now(clock));
    }
}
