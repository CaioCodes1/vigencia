package com.caiocodes.crbap.shared.domain;

import com.caiocodes.crbap.shared.domain.exception.InvalidDateRangeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Período de vigência: início e fim, com o fim sempre depois do início.
 *
 * <p>Repare que todo método que depende de "hoje" recebe a data como
 * parâmetro em vez de chamar {@code LocalDate.now()}. Sem isso, testar
 * "contrato expirado há 31 dias" exigiria mexer no relógio da máquina.
 */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        Objects.requireNonNull(start, "a data de início é obrigatória");
        Objects.requireNonNull(end, "a data de fim é obrigatória");
        if (!start.isBefore(end)) {
            throw new InvalidDateRangeException(start, end);
        }
    }

    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start, end);
    }

    /** Dias entre a referência e o fim. Negativo quando o fim já passou. */
    public long daysUntilEnd(LocalDate reference) {
        return ChronoUnit.DAYS.between(reference, end);
    }

    public long totalDays() {
        return ChronoUnit.DAYS.between(start, end);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public boolean isExpiredOn(LocalDate reference) {
        return reference.isAfter(end);
    }

    public DateRange withEnd(LocalDate newEnd) {
        return new DateRange(start, newEnd);
    }

    /** Período seguinte, começando no dia após este terminar. */
    public DateRange nextPeriod(Period duration) {
        LocalDate nextStart = end.plusDays(1);
        return new DateRange(nextStart, nextStart.plus(duration).minusDays(1));
    }

    @Override
    public String toString() {
        return start + " a " + end;
    }
}
