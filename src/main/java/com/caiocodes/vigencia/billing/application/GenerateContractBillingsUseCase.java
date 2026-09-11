package com.caiocodes.vigencia.billing.application;

import com.caiocodes.vigencia.billing.domain.Billing;
import com.caiocodes.vigencia.billing.domain.BillingId;
import com.caiocodes.vigencia.billing.domain.BillingRepository;
import com.caiocodes.vigencia.billing.domain.BillingScheduleCalculator;
import com.caiocodes.vigencia.billing.domain.BillingScheduleCalculator.BillingDraft;
import com.caiocodes.vigencia.billing.domain.NewBilling;
import com.caiocodes.vigencia.contract.application.port.ContractBillingPort.ActivatedContract;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera as parcelas de um contrato recém-ativado (RF-15).
 *
 * <p>{@code Propagation.MANDATORY}: <b>exige</b> a transação da ativação e falha
 * se for chamado fora dela. É o que transforma o ADR-006 de intenção em
 * garantia — sem isso, um refactor futuro poderia dar a este método uma
 * transação própria e reabrir a janela em que o contrato está ativo sem
 * cobrança.
 *
 * <p>Também é idempotente: pular as parcelas que já existem faz uma ativação
 * repetida (retry de rede, reprocessamento) não duplicar nada. A garantia final
 * continua sendo o índice {@code uk_billings_contract_installment}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateContractBillingsUseCase {

    private final BillingRepository billings;
    private final DomainEventRecorder events;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public int execute(ActivatedContract contract) {
        LocalDate hoje = LocalDate.now(clock);

        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                contract.period(), contract.value(), contract.cycle(),
                contract.billingDay(), contract.gracePeriodDays());

        List<Billing> novas = new ArrayList<>(parcelas.size());
        for (BillingDraft draft : parcelas) {
            if (billings.existsByContractIdAndInstallment(
                    contract.contractId(), draft.installment())) {
                continue;
            }
            novas.add(Billing.issue(new NewBilling(
                    contract.clientId(),
                    contract.contractId(),
                    referencia(contract, draft),
                    draft.installment(),
                    draft.totalInstallments(),
                    draft.amount(),
                    draft.dueDate(),
                    hoje,
                    null,
                    null)));
        }

        if (novas.isEmpty()) {
            log.info("billing.generate contractId={} nada a gerar (ja existiam)",
                    contract.contractId());
            return 0;
        }

        List<Billing> salvas = billings.saveAll(novas);
        salvas.forEach(events::record);
        log.info("billing.generate contractId={} parcelas={} total={}",
                contract.contractId(), salvas.size(), contract.value());
        return salvas.size();
    }

    /**
     * Cancela o que ainda não venceu de um contrato.
     *
     * <p>As vencidas continuam de pé: cancelar contrato não perdoa dívida.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelFuture(UUID contractId, LocalDate from, String reason) {
        List<BillingId> abertas = billings.findOpenByContractDueFrom(contractId, from);
        int canceladas = 0;
        for (BillingId id : abertas) {
            Billing billing = billings.findByIdForUpdate(id).orElse(null);
            if (billing == null || !billing.totalPaid().isZero()) {
                // Recebeu algo: cancelar esconderia o pagamento. Fica de pé para
                // o financeiro decidir entre estorno e acerto.
                continue;
            }
            billing.cancel(reason);
            Billing salva = billings.save(billing);
            events.record(salva);
            canceladas++;
        }
        if (canceladas > 0) {
            log.info("billing.cancel_future contractId={} canceladas={}", contractId, canceladas);
        }
        return canceladas;
    }

    /** {@code CT-2026-0042 3/12}, ou só o número quando é pagamento único. */
    private static String referencia(ActivatedContract contract, BillingDraft draft) {
        if (draft.totalInstallments() <= 1) {
            return contract.number();
        }
        return "%s %d/%d".formatted(contract.number(), draft.installment(),
                draft.totalInstallments());
    }
}
