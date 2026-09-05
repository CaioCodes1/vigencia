package com.caiocodes.crbap.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O painel que a dona da empresa abre de manhã.
 *
 * <p>Quatro perguntas, uma resposta só: quantos contratos estão de pé e
 * quantos vencem; quanto entrou e quanto era para entrar; que fração dos
 * contratos vencidos foi renovada; e quanto está atrasado.
 *
 * <p>É um objeto de leitura, montado a partir de {@code SELECT ... GROUP BY} —
 * não tem agregado por trás, e não deve ter. Ver o {@code package-info} do
 * módulo.
 */
public record DashboardSummary(
        LocalDate referenceDate,
        Contracts contracts,
        Revenue revenue,
        RenewalRate renewalRate,
        Delinquency delinquency) {

    public record Contracts(
            long active,
            long expiringIn30Days,
            long draft,
            long expiredThisMonth,
            String activeChangeVsLastMonth) {
    }

    public record Revenue(
            Amounts currentMonth,
            Amounts currentYear,
            BigDecimal mrr,
            BigDecimal arr,
            String currency) {
    }

    public record Amounts(BigDecimal realized, BigDecimal expected) {
    }

    public record Delinquency(
            long clients,
            BigDecimal totalOverdue,
            long oldestOverdueDays,
            BigDecimal percentOfExpected) {
    }
}
