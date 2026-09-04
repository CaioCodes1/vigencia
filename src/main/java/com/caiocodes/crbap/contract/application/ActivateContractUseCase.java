package com.caiocodes.crbap.contract.application;

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
 * DRAFT → ACTIVE (RF-09).
 *
 * <p>É o evento {@code contract.activated} gravado aqui que a fase 5 vai
 * consumir para gerar as cobranças do ciclo. Esta transação não gera cobrança
 * nenhuma de propósito: ativar contrato e faturar são responsabilidades de
 * módulos diferentes, e amarrar as duas no mesmo commit faria uma falha no
 * faturamento impedir a ativação.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateContractUseCase {

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    public ContractDetail execute(ContractId id) {
        Contract contract = finder.requireForUpdate(id);
        contract.activate(clock.instant());

        Contract saved = contracts.save(contract);
        events.record(saved);
        log.info("contract.activated contractId={} number={}", saved.id(), saved.number());
        return ContractDetail.from(saved, finder.clientNameOf(saved), LocalDate.now(clock));
    }
}
