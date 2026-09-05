package com.caiocodes.crbap.reporting.application;

import com.caiocodes.crbap.reporting.application.port.DashboardQueries;
import com.caiocodes.crbap.reporting.application.port.DashboardQueries.ContractCounts;
import com.caiocodes.crbap.reporting.application.port.DashboardQueries.DelinquencyTotals;
import com.caiocodes.crbap.reporting.application.port.DashboardQueries.RevenueTotals;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * A camada de cache do painel — e o lugar onde as consultas viram resposta.
 *
 * <p><b>Toda chave carrega o escopo.</b> {@code key = "#scope.cacheKey()"} é a
 * linha mais importante deste arquivo: com {@code key = "'summary'"}, o
 * primeiro a abrir o painel encheria o cache e todos os outros receberiam a
 * resposta dele — o vendedor veria a empresa inteira. Vazamento de autorização
 * por cache não quebra tela nenhuma; ele entrega o dado errado com cara de
 * certo, e só aparece quando alguém repara em um número que não devia ver.
 *
 * <p><b>Por que classe separada do caso de uso:</b> o {@code @Cacheable} é
 * atendido pelo proxy do Spring. Se o caso de uso resolvesse o escopo e
 * chamasse o próprio método anotado, seria autoinvocação — o proxy não entra, e
 * o cache simplesmente não existiria, sem nenhum erro. É a mesma armadilha do
 * {@code REQUIRES_NEW} que custou caro na fase 6.
 *
 * <p><b>TTL curto em vez de invalidação por evento:</b> o painel atrasado em
 * cinco minutos não faz ninguém decidir errado — ele mostra tendência, não
 * saldo. Invalidação por evento custaria acertar o {@code AFTER_COMMIT} de cada
 * um dos sete eventos do sistema, e errar um deixa o cache mentindo até o TTL,
 * que é o pior dos dois mundos.
 */
@Component
@RequiredArgsConstructor
public class DashboardCache {

    public static final String SUMMARY = "dashboard:summary";
    public static final String REVENUE = "dashboard:revenue";
    public static final String RENEWAL = "dashboard:renewal-rate";
    public static final String DELINQUENT = "dashboard:delinquent";
    public static final String EXPIRING = "dashboard:expiring";

    /**
     * O painel soma sem separar moeda.
     *
     * <p>Assumido: hoje toda a base é BRL, e o {@code CHECK} do banco aceita
     * outras. No dia em que existir um contrato em dólar, estes totais passam a
     * somar laranja com maçã — e a correção é {@code GROUP BY currency} nas
     * consultas, não um fator de conversão aqui.
     */
    private static final String MOEDA = "BRL";

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal DOZE = BigDecimal.valueOf(12);

    private final DashboardQueries queries;
    private final Clock clock;

    @Cacheable(cacheNames = SUMMARY, key = "#scope.cacheKey()")
    public DashboardSummary summary(DashboardScope scope) {
        return montarResumo(scope);
    }

    /**
     * Recalcula e regrava, para o job de aquecimento.
     *
     * <p>{@code @CachePut} e não {@code @Cacheable}: o segundo devolveria o
     * valor que ainda está lá e não recalcularia nada — o aquecimento não
     * aqueceria coisa alguma.
     */
    @CachePut(cacheNames = SUMMARY, key = "#scope.cacheKey()")
    public DashboardSummary refreshSummary(DashboardScope scope) {
        return montarResumo(scope);
    }

    @Cacheable(cacheNames = REVENUE, key = "#scope.cacheKey() + ':' + #from + ':' + #until")
    public List<RevenuePoint> revenue(DashboardScope scope, LocalDate from, LocalDate until) {
        return queries.revenueSeries(scope, from, until);
    }

    @Cacheable(cacheNames = RENEWAL, key = "#scope.cacheKey() + ':' + #from + ':' + #until")
    public RenewalRate renewalRate(DashboardScope scope, LocalDate from, LocalDate until) {
        return queries.renewalRate(scope, from, until, from + " a " + until);
    }

    @Cacheable(cacheNames = DELINQUENT, key = "#scope.cacheKey() + ':' + #page + ':' + #size")
    public PageResult<DelinquentClient> delinquentClients(DashboardScope scope,
                                                          int page, int size) {
        return queries.delinquentClients(scope, LocalDate.now(clock), page, size);
    }

    @Cacheable(cacheNames = EXPIRING, key = "#scope.cacheKey() + ':' + #daysAhead")
    public List<ExpiringBucket> expiringTimeline(DashboardScope scope, int daysAhead) {
        return queries.expiringTimeline(scope, LocalDate.now(clock), daysAhead);
    }

    private DashboardSummary montarResumo(DashboardScope scope) {
        LocalDate hoje = LocalDate.now(clock);
        ContractCounts contagens = queries.contractCounts(scope, hoje);
        RevenueTotals receita = queries.revenueTotals(scope, hoje);
        DelinquencyTotals atraso = queries.delinquency(scope, hoje);

        LocalDate inicioDoTrimestre = hoje.withMonth(((hoje.getMonthValue() - 1) / 3) * 3 + 1)
                .withDayOfMonth(1);
        RenewalRate taxa = queries.renewalRate(scope, inicioDoTrimestre,
                inicioDoTrimestre.plusMonths(3).minusDays(1), rotuloDoTrimestre(hoje));

        return new DashboardSummary(
                hoje,
                new DashboardSummary.Contracts(
                        contagens.active(), contagens.expiringIn30Days(), contagens.draft(),
                        contagens.expiredThisMonth(),
                        variacao(contagens.active(), contagens.activeLastMonth())),
                new DashboardSummary.Revenue(
                        new DashboardSummary.Amounts(
                                receita.realizedMonth(), receita.expectedMonth()),
                        new DashboardSummary.Amounts(
                                receita.realizedYear(), receita.expectedYear()),
                        receita.mrr(),
                        receita.mrr().multiply(DOZE).setScale(2, RoundingMode.HALF_UP),
                        MOEDA),
                taxa,
                new DashboardSummary.Delinquency(
                        atraso.clients(), atraso.totalOverdue(), atraso.oldestOverdueDays(),
                        percentual(atraso.totalOverdue(), receita.expectedYear())));
    }

    private static String rotuloDoTrimestre(LocalDate dia) {
        return "%d-Q%d".formatted(dia.getYear(), (dia.getMonthValue() - 1) / 3 + 1);
    }

    /**
     * A seta de variação: {@code "+5.4%"}.
     *
     * <p>Base zero devolve {@code "—"} e não {@code "+100%"}: sair de nenhum
     * contrato para um não é crescimento de cem por cento, é o primeiro mês.
     */
    private static String variacao(long agora, long antes) {
        if (antes == 0) {
            return "—";
        }
        BigDecimal delta = BigDecimal.valueOf(agora - antes)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(antes), 1, RoundingMode.HALF_UP);
        return (delta.signum() >= 0 ? "+" : "") + delta.toPlainString() + "%";
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

}
