package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.contract.application.port.ContractBillingPort;
import com.caiocodes.vigencia.contract.domain.Contract;
import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.shared.application.ClientDirectory;
import com.caiocodes.vigencia.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import com.caiocodes.vigencia.shared.domain.DateRange;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renova <b>um</b> contrato automaticamente, em transação própria.
 *
 * <p>Classe separada pela terceira vez no projeto, e sempre pelo mesmo motivo:
 * {@code @Transactional(REQUIRES_NEW)} num método chamado de outro método da
 * mesma classe é silenciosamente ignorado pelo proxy do Spring. Ver o Javadoc
 * do {@code ContractExpirer}.
 *
 * <p>Não passa pelo {@code ContractFinder}: o escopo de carteira sai de quem
 * está autenticado, e num job não há ninguém autenticado. É o sistema agindo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractAutoRenewer {

    private final ContractRepository contracts;
    private final ClientDirectory clients;
    private final ContractBillingPort billings;
    private final DomainEventRecorder events;
    private final Clock clock;

    /** @return true se o contrato foi renovado */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(ContractId id, LocalDate hoje) {
        Contract atual = contracts.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Contrato sumiu: " + id));

        ClientRef cliente = clients.findRef(atual.clientId()).orElse(null);
        if (cliente == null || !cliente.active()) {
            // Renovar automaticamente para cliente inativo e exatamente o
            // problema que a planilha tinha: cobranca nascendo sozinha para
            // quem ja saiu.
            log.info("contract.auto_renew.pulado motivo=cliente_inativo contractId={}", id);
            return false;
        }

        // Mesma duracao do ciclo e mesmo valor: reajuste e decisao comercial, e
        // um job nao toma decisao comercial.
        DateRange novoPeriodo = atual.cycle().nextPeriodAfter(atual.period());
        Contract sucessor = atual.renew(novoPeriodo, atual.value(), hoje, clock.instant(), null);

        contracts.save(atual);
        Contract salvo = contracts.save(sucessor);
        events.record(atual);
        events.record(salvo);

        billings.generateFor(ContractBillings.of(salvo));
        log.info("contract.auto_renewed de={} para={} number={}",
                atual.id(), salvo.id(), salvo.number());
        return true;
    }
}
