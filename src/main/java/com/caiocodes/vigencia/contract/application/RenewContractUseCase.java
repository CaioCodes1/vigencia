package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.contract.application.port.ContractBillingPort;
import com.caiocodes.vigencia.contract.domain.Contract;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.shared.application.BusinessMetrics;
import com.caiocodes.vigencia.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import com.caiocodes.vigencia.shared.domain.DateRange;
import com.caiocodes.vigencia.shared.domain.Money;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renovação (RF-10) — o caso de uso mais delicado do sistema.
 *
 * <p>Três problemas diferentes acontecem aqui, e cada um tem sua defesa:
 *
 * <ol>
 *   <li><b>O usuário clica duas vezes.</b> Resolve-se com a
 *       {@code Idempotency-Key}: a chave é gravada no contrato sucessor, e a
 *       segunda chamada com a mesma chave devolve o mesmo contrato com 200.</li>
 *   <li><b>Duas requisições chegam ao mesmo tempo</b> (dois operadores, ou o
 *       clique duplo antes de a primeira commitar). A chave ainda não está
 *       gravada quando a segunda faz a leitura, então a idempotência sozinha não
 *       basta. Quem resolve é o {@code SELECT ... FOR UPDATE}: a segunda espera,
 *       e quando entra encontra o contrato já em {@code RENEWED} — o agregado
 *       recusa e ela recebe 409.</li>
 *   <li><b>Metade renovada.</b> Renovar grava <i>dois</i> agregados: o anterior
 *       vira {@code RENEWED} e o sucessor nasce {@code ACTIVE}. As duas
 *       gravações e os eventos ficam na mesma transação — um commit ou
 *       nenhum.</li>
 * </ol>
 *
 * <p>A rede de segurança final é do banco: {@code uk_contracts_one_successor}
 * impede que qualquer caminho que escape do lock deixe a cadeia bifurcada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewContractUseCase {

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final ContractBillingPort billings;
    private final DomainEventRecorder events;
    private final BusinessMetrics metrics;
    private final Clock clock;

    /**
     * @param repeated true quando a chamada foi repetida com a mesma chave — é
     *                 o que faz o controller responder 200 em vez de 201
     */
    public record RenewalResult(ContractDetail contract, boolean repeated) {
    }

    @Transactional
    // O id auditado é o do contrato RENOVADO, não o do sucessor: a pergunta
    // que a trilha recebe é "o que aconteceu com este contrato?". O sucessor
    // aparece no after_data, com número e valor novos.
    @Auditable(entity = "Contract", action = "RENEW", id = "#command.contractId().value()")
    public RenewalResult execute(ContractCommands.RenewContract command) {
        LocalDate today = LocalDate.now(clock);

        Optional<Contract> jaRenovado = alreadyRenewed(command.idempotencyKey());
        if (jaRenovado.isPresent()) {
            Contract existente = jaRenovado.get();
            log.info("contract.renew.repeated contractId={} key={}",
                    existente.id(), command.idempotencyKey());
            return new RenewalResult(
                    ContractDetail.from(existente, finder.clientNameOf(existente), today), true);
        }

        Contract atual = finder.requireForUpdate(command.contractId());
        requireActiveClient(atual);

        DateRange novoPeriodo = DateRange.of(
                atual.period().end().plusDays(1),
                command.endDate());
        Money novoValor = command.amount() == null
                ? atual.value()
                : CreateContractUseCase.money(command.amount(), command.currency());

        Contract sucessor = atual.renew(novoPeriodo, novoValor, today, clock.instant(),
                command.idempotencyKey());

        contracts.save(atual);
        Contract salvo = contracts.save(sucessor);
        events.record(atual);
        events.record(salvo);

        // O sucessor nasce ACTIVE, entao as parcelas dele saem aqui — na mesma
        // transacao, pelo mesmo motivo da ativacao (ADR-006). O contrato
        // anterior tem as cobrancas futuras canceladas: ele acabou hoje.
        billings.cancelFutureFor(atual.id().value(), today,
                "Contrato renovado pelo sucessor " + salvo.number());
        int cobrancas = billings.generateFor(ContractBillings.of(salvo));

        metrics.contractRenewed(salvo.value().amount());
        log.info("contract.renewed de={} para={} number={} cobrancas={}",
                atual.id(), salvo.id(), salvo.number(), cobrancas);
        return new RenewalResult(
                ContractDetail.from(salvo, finder.clientNameOf(salvo), today), false);
    }

    private Optional<Contract> alreadyRenewed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return contracts.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Cliente inativo não renova.
     *
     * <p>Sem esta regra, um cliente desativado por inadimplência continuaria
     * gerando cobranças por renovação automática — que é justamente o problema
     * que a planilha tinha.
     */
    private void requireActiveClient(Contract contract) {
        ClientRef ref = finder.clientRefOf(contract);
        if (ref == null || !ref.active()) {
            throw new BusinessRuleException("CLIENT_NOT_ACTIVE",
                    "Não é possível renovar contrato de cliente inativo");
        }
    }
}
