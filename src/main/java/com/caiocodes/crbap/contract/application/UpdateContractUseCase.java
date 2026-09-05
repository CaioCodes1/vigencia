package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.audit.application.AuditContext;
import com.caiocodes.crbap.audit.application.Auditable;
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
    @Auditable(entity = "Contract", action = "UPDATE")
    public ContractDetail execute(ContractCommands.UpdateContract command) {
        Contract contract = finder.requireForUpdate(command.contractId());
        LocalDate today = LocalDate.now(clock);
        String clientName = finder.clientNameOf(contract);

        // A foto do estado anterior, no mesmo formato do retorno: é o que
        // permite ao aspecto dizer "mudou o valor e a data de fim", em vez de
        // deixar dois JSONs para alguém comparar no olho.
        AuditContext.before(ContractDetail.from(contract, clientName, today));

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
        return ContractDetail.from(saved, clientName, today);
    }
}
