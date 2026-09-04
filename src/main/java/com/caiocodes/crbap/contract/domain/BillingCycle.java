package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.DateRange;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * Periodicidade da cobrança de um contrato.
 *
 * <p>O {@link Period} embutido é o que permite calcular "quantas parcelas cabem
 * neste período" e "quando cai a parcela nº 3" sem um {@code switch} espalhado
 * pelo código — quem sabe disso é o próprio ciclo.
 */
public enum BillingCycle {

    MONTHLY(Period.ofMonths(1)),
    QUARTERLY(Period.ofMonths(3)),
    SEMIANNUAL(Period.ofMonths(6)),
    YEARLY(Period.ofYears(1)),

    /** Pagamento único: não se repete, então não tem período. */
    ONE_TIME(null);

    private final Period period;

    BillingCycle(Period period) {
        this.period = period;
    }

    public Period period() {
        return period;
    }

    public boolean isRecurring() {
        return period != null;
    }

    /**
     * Quantas cobranças cabem no período de vigência.
     *
     * <p>Sempre pelo menos 1: um contrato anual de 6 meses ainda gera uma
     * cobrança. Arredondar para baixo e devolver 0 faria o contrato existir sem
     * nunca ser faturado.
     */
    public int occurrencesIn(DateRange range) {
        if (!isRecurring()) {
            return 1;
        }
        long months = ChronoUnit.MONTHS.between(range.start(), range.end().plusDays(1));
        long step = period.toTotalMonths();
        return (int) Math.max(1, months / step);
    }

    /** Vencimento da parcela de índice {@code index} (base zero). */
    public LocalDate nextDueDate(LocalDate from, int index) {
        if (!isRecurring()) {
            return from;
        }
        return from.plus(period.multipliedBy(index));
    }

    /** Período seguinte de mesma duração, para a renovação padrão. */
    public DateRange nextPeriodAfter(DateRange current) {
        Period duration = isRecurring() ? period : Period.ofYears(1);
        return current.nextPeriod(duration);
    }
}
