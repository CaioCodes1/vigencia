package com.caiocodes.crbap.contract.application.port;

import com.caiocodes.crbap.shared.domain.BillingCycle;
import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O que o módulo de contratos precisa que aconteça em cobranças.
 *
 * <p><b>Chamada síncrona, dentro da transação da ativação</b> — ver ADR-006.
 * Contraria a regra geral de "um agregado por transação", e é consciente: se a
 * geração das cobranças fosse um evento assíncrono, existiria uma janela em que
 * o contrato está ativo e não tem cobrança nenhuma. Se o consumidor falhasse e
 * fosse para a DLQ, a empresa deixaria de faturar aquele contrato e <b>ninguém
 * perceberia</b>. Onde há dinheiro, não há consistência eventual.
 *
 * <p>A porta é declarada aqui, no módulo que precisa, e implementada por
 * {@code billing}. É por isso que {@code contract} não importa uma linha de
 * cobranças, mesmo dependendo delas para funcionar.
 */
public interface ContractBillingPort {

    /**
     * Gera as parcelas do contrato recém-ativado.
     *
     * <p>É idempotente: chamada duas vezes para o mesmo contrato não duplica
     * parcela, porque a existência de cada uma é conferida e o índice
     * {@code uk_billings_contract_installment} é a garantia final.
     *
     * @return quantas cobranças foram criadas
     */
    int generateFor(ActivatedContract contract);

    /**
     * Cancela as cobranças em aberto com vencimento a partir de {@code from}.
     *
     * <p>As <b>vencidas continuam de pé</b>: cancelar contrato não perdoa
     * dívida. Quem decide o que fazer com o que já venceu é o financeiro.
     *
     * @return quantas cobranças foram canceladas
     */
    int cancelFutureFor(UUID contractId, LocalDate from, String reason);

    /**
     * O contrato do ponto de vista de quem fatura.
     *
     * <p>Só os campos que definem as parcelas. Passar o agregado
     * {@code Contract} obrigaria {@code billing} a importar o domínio de
     * contratos — e a partir daí nada impediria alguém de chamar
     * {@code contract.renew()} de dentro do módulo de cobranças.
     */
    record ActivatedContract(
            UUID contractId,
            UUID clientId,
            String number,
            DateRange period,
            Money value,
            BillingCycle cycle,
            Integer billingDay,
            int gracePeriodDays) {
    }
}
