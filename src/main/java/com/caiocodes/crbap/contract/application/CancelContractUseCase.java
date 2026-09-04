package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractRepository;
import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelamento (RF-12).
 *
 * <p>O motivo é obrigatório e tem mínimo de 10 caracteres. Não é burocracia: o
 * cancelamento de contrato é o evento que mais gera discussão depois, e "ok"
 * como justificativa não responde nada seis meses adiante.
 *
 * <p>As cobranças <b>vencidas continuam de pé</b> — cancelar contrato não
 * perdoa dívida. Quem cancela as cobranças futuras é o consumidor de
 * {@code contract.cancelled} na fase 5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelContractUseCase {

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional
    public ContractDetail execute(ContractCommands.CancelContract command) {
        LocalDate today = LocalDate.now(clock);
        Contract contract = finder.requireForUpdate(command.contractId());

        contract.cancel(command.reason(), today, clock.instant());

        Contract saved = contracts.save(contract);
        events.record(saved);
        log.info("contract.cancelled contractId={} number={}", saved.id(), saved.number());
        return ContractDetail.from(saved, finder.clientNameOf(saved), today);
    }
}
