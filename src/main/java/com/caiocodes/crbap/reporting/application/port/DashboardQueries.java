package com.caiocodes.crbap.reporting.application.port;

import com.caiocodes.crbap.reporting.application.DelinquentClient;
import com.caiocodes.crbap.reporting.application.ExpiringBucket;
import com.caiocodes.crbap.reporting.application.RenewalRate;
import com.caiocodes.crbap.reporting.application.RevenuePoint;
import com.caiocodes.crbap.reporting.application.DashboardScope;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * As consultas analíticas do painel.
 *
 * <p>Porta declarada aqui e implementada em JDBC, com SQL escrito à mão. É a
 * parte de leitura de um CQRS <i>light</i>: a escrita passa pelo domínio e
 * pelos agregados; a leitura de relatório vai reto ao banco, porque
 * {@code GROUP BY} de milhões de linhas montado por ORM é lento de um jeito que
 * não se conserta depois.
 *
 * <p><b>Todo método recebe o escopo.</b> Não existe sobrecarga "sem escopo" de
 * propósito: a assinatura obriga quem escrever a próxima consulta a decidir o
 * que fazer com a carteira, em vez de esquecer que ela existe.
 */
public interface DashboardQueries {

    ContractCounts contractCounts(DashboardScope scope, LocalDate today);

    RevenueTotals revenueTotals(DashboardScope scope, LocalDate today);

    RenewalRate renewalRate(DashboardScope scope, LocalDate from, LocalDate until, String label);

    DelinquencyTotals delinquency(DashboardScope scope, LocalDate today);

    List<RevenuePoint> revenueSeries(DashboardScope scope, LocalDate from, LocalDate until);

    PageResult<DelinquentClient> delinquentClients(DashboardScope scope, LocalDate today,
                                                   int page, int size);

    List<ExpiringBucket> expiringTimeline(DashboardScope scope, LocalDate today, int daysAhead);

    /** Contagens de contrato, incluindo a base do mês passado para a variação. */
    record ContractCounts(
            long active,
            long expiringIn30Days,
            long draft,
            long expiredThisMonth,
            long activeLastMonth) {
    }

    /** Recebido e previsto, do mês e do ano, mais a receita recorrente mensal. */
    record RevenueTotals(
            BigDecimal realizedMonth,
            BigDecimal expectedMonth,
            BigDecimal realizedYear,
            BigDecimal expectedYear,
            BigDecimal mrr) {
    }

    record DelinquencyTotals(long clients, BigDecimal totalOverdue, long oldestOverdueDays) {
    }
}
