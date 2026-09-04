package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractRepository;
import com.caiocodes.crbap.shared.domain.DateRange;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edição do contrato — só em DRAFT.
 *
 * <p>Nenhum campo de identidade entra aqui: número, cliente e status ficam de
 * fora porque mudar qualquer um deles não é "editar", é outro contrato.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateContractUseCase {

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final Clock clock;

    @Transactional
    public ContractDetail execute(ContractCommands.UpdateContract command) {
        Contract contract = finder.requireForUpdate(command.contractId());

        contract.updateDraft(
                command.title(),
                command.description(),
                DateRange.of(command.startDate(), command.endDate()),
                CreateContractUseCase.money(command.amount(), command.currency()),
                command.billingCycle(),
                command.billingDay(),
                command.gracePeriodDays(),
                command.autoRenew());

        Contract saved = contracts.save(contract);
        log.info("contract.updated contractId={}", saved.id());
        return ContractDetail.from(saved, finder.clientNameOf(saved), LocalDate.now(clock));
    }
}
