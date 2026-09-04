package com.caiocodes.crbap.billing.domain;

import com.caiocodes.crbap.shared.domain.BillingCycle;
import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dado um contrato, diz quais cobranças criar.
 *
 * <p><b>É um serviço de domínio, e não um método de agregado</b>, porque a
 * regra não cabe em nenhum dos dois: contrato não cria cobrança (é outro
 * agregado) e uma cobrança não sabe das irmãs — só o conjunto tem sentido. É
 * exatamente o caso em que um serviço de domínio se justifica; forçar isso
 * dentro de {@code Contract} seria dar a ele responsabilidade sobre dinheiro.
 *
 * <p>Sem estado e sem dependência: só entra e sai valor. Por isso é testável
 * sem contexto nenhum, e é onde mora o teste mais importante da fase — o que
 * confere que <b>a soma das parcelas é exatamente o valor do contrato</b>.
 */
public final class BillingScheduleCalculator {

    private BillingScheduleCalculator() {
    }

    /**
     * @param gracePeriodDays dias entre a data-base da parcela e o vencimento
     * @param billingDay      dia fixo do mês para vencer (1–28), ou nulo para
     *                        seguir a data de início do contrato
     */
    public static List<BillingDraft> from(DateRange period, Money total, BillingCycle cycle,
                                          Integer billingDay, int gracePeriodDays) {
        if (!cycle.isRecurring()) {
            return List.of(new BillingDraft(total, 1, 1,
                    dueDate(period.start(), billingDay, gracePeriodDays)));
        }

        // O ciclo diz quantas parcelas cabem no período; o valor diz quantas
        // cabem no dinheiro. R$ 0,01 em 12 parcelas daria onze de R$ 0,00, e
        // cobrança de valor zero é recusada pelo agregado — o cálculo não pode
        // produzi-la em silêncio para o INSERT estourar depois.
        int parcelas = Math.min(cycle.occurrencesIn(period), (int) total.toMinorUnits());
        parcelas = Math.max(1, parcelas);
        List<Money> valores = total.split(parcelas);

        List<BillingDraft> drafts = new ArrayList<>(parcelas);
        for (int i = 0; i < parcelas; i++) {
            LocalDate base = cycle.nextDueDate(period.start(), i);
            drafts.add(new BillingDraft(valores.get(i), i + 1, parcelas,
                    dueDate(base, billingDay, gracePeriodDays)));
        }
        return List.copyOf(drafts);
    }

    /**
     * O dia de cobrança nunca empurra o vencimento para <b>antes</b> da data-base
     * da parcela.
     *
     * <p>Contrato que começa dia 20 com {@code billingDay = 10} venceria dia 10
     * — dez dias antes de o serviço existir. Nesse caso a parcela cai no mês
     * seguinte, que é o que qualquer financeiro faria na mão.
     */
    private static LocalDate dueDate(LocalDate base, Integer billingDay, int gracePeriodDays) {
        LocalDate vencimento = base;
        if (billingDay != null) {
            vencimento = base.withDayOfMonth(billingDay);
            if (vencimento.isBefore(base)) {
                vencimento = vencimento.plusMonths(1);
            }
        }
        return vencimento.plusDays(gracePeriodDays);
    }

    /**
     * Uma cobrança a criar — ainda sem cliente, sem id e sem contrato.
     *
     * <p>É o resultado do cálculo, não a cobrança: quem transforma em
     * {@link Billing} é o caso de uso, que é quem sabe de qual contrato e de
     * qual cliente se trata.
     */
    public record BillingDraft(Money amount, int installment, int totalInstallments,
                               LocalDate dueDate) {
    }
}
