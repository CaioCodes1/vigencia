package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.audit.application.Auditable;
import com.caiocodes.crbap.contract.application.port.ContractBillingPort;
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
 * DRAFT → ACTIVE (RF-09), gerando as cobranças do ciclo.
 *
 * <p>As cobranças nascem <b>na mesma transação</b> (ADR-006). É a exceção
 * consciente à regra de um agregado por transação: um contrato ativo sem
 * cobrança é uma empresa que parou de faturar sem ninguém perceber, e nenhuma
 * janela de inconsistência é aceitável aí. Um commit ou nenhum.
 *
 * <p>O e-mail de boas-vindas continua assíncrono, pela outbox — esse sim
 * tolera atraso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateContractUseCase {

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final ContractBillingPort billings;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "Contract", action = "ACTIVATE")
    public ContractDetail execute(ContractId id) {
        Contract contract = finder.requireForUpdate(id);
        contract.activate(clock.instant());

        Contract saved = contracts.save(contract);
        events.record(saved);

        int cobrancas = billings.generateFor(ContractBillings.of(saved));
        log.info("contract.activated contractId={} number={} cobrancas={}",
                saved.id(), saved.number(), cobrancas);
        return ContractDetail.from(saved, finder.clientNameOf(saved), LocalDate.now(clock));
    }
}
