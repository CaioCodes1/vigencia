package com.caiocodes.crbap.reporting.application;

import com.caiocodes.crbap.shared.application.CurrentUser;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * As leituras do painel.
 *
 * <p>A única responsabilidade deste caso de uso é <b>resolver o escopo</b> e
 * repassar. Quem tem {@code dashboard:read_all} vê a empresa; quem não tem vê a
 * própria carteira — e o id da carteira vem de quem está autenticado, nunca de
 * um parâmetro da URL. Trocar o {@code accountManagerId} na query string não
 * muda nada, porque ele não existe na query string.
 *
 * <p>Sem {@code @Transactional}: são consultas analíticas de leitura, cada uma
 * resolvida em uma ida ao banco, e a maioria nem chega lá — o cache responde.
 * Abrir transação aqui seguraria conexão do pool por consulta de painel, que é
 * o tipo de coisa que ninguém percebe até o dia de pico.
 */
@Service
@RequiredArgsConstructor
public class GetDashboardUseCase {

    static final String READ_ALL = "dashboard:read_all";

    private final DashboardCache cache;
    private final CurrentUser currentUser;
    private final Clock clock;

    public DashboardSummary summary() {
        return cache.summary(scope());
    }

    /**
     * Série de receita. Sem período, os doze meses que terminam hoje.
     *
     * <p>Doze e não "desde sempre": o gráfico tem largura finita, e a consulta
     * sem piso vira uma varredura da história inteira a cada abertura do painel.
     */
    public List<RevenuePoint> revenue(LocalDate from, LocalDate until) {
        LocalDate fim = until == null ? LocalDate.now(clock) : until;
        LocalDate inicio = from == null ? fim.minusMonths(11).withDayOfMonth(1) : from;
        return cache.revenue(scope(), inicio, fim);
    }

    public RenewalRate renewalRate(LocalDate from, LocalDate until) {
        LocalDate fim = until == null ? LocalDate.now(clock) : until;
        LocalDate inicio = from == null ? fim.minusMonths(11).withDayOfMonth(1) : from;
        return cache.renewalRate(scope(), inicio, fim);
    }

    public PageResult<DelinquentClient> delinquentClients(int page, int size) {
        return cache.delinquentClients(scope(), Math.max(0, page), limite(size));
    }

    public List<ExpiringBucket> expiringTimeline(int daysAhead) {
        return cache.expiringTimeline(scope(), Math.min(Math.max(1, daysAhead), 365));
    }

    private DashboardScope scope() {
        return new DashboardScope(currentUser.scopeOrNull(READ_ALL));
    }

    private static int limite(int size) {
        return Math.min(Math.max(1, size), 100);
    }
}
