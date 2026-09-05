package com.caiocodes.crbap.reporting.infrastructure.persistence;

import com.caiocodes.crbap.reporting.application.DashboardScope;
import com.caiocodes.crbap.reporting.application.DelinquentClient;
import com.caiocodes.crbap.reporting.application.ExpiringBucket;
import com.caiocodes.crbap.reporting.application.RenewalRate;
import com.caiocodes.crbap.reporting.application.RevenuePoint;
import com.caiocodes.crbap.reporting.application.port.DashboardQueries;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As consultas do painel, em SQL escrito à mão.
 *
 * <p><b>Sobre o filtro de escopo {@code (:scope IS NULL OR ...)}:</b> no
 * adaptador de auditoria esse mesmo padrão foi rejeitado, porque lá ele impede
 * o Postgres de usar o índice para achar poucas linhas em muitas. Aqui é o
 * contrário: toda consulta é uma agregação que varre o conjunto de qualquer
 * jeito, e o filtro só reduz o que entra na conta. O padrão não é bom nem ruim
 * por si — depende de o plano depender dele.
 *
 * <p><b>Sobre {@code FILTER (WHERE ...)}:</b> é a forma do Postgres de contar
 * condicionalmente em uma passada só. As quatro contagens de contrato saem de
 * uma varredura, não de quatro.
 */
@Repository
@RequiredArgsConstructor
class JdbcDashboardQueries implements DashboardQueries {

    /** Repetido em toda consulta: ou vê tudo, ou vê a própria carteira. */
    private static final String ESCOPO =
            "(CAST(:scope AS uuid) IS NULL OR cl.account_manager_id = CAST(:scope AS uuid))";

    private static final String CONTAGENS = """
            SELECT
              count(*) FILTER (WHERE c.status = 'ACTIVE') AS active,
              count(*) FILTER (WHERE c.status = 'ACTIVE'
                                 AND c.end_date BETWEEN :hoje AND :em30) AS expiring_30,
              count(*) FILTER (WHERE c.status = 'DRAFT') AS draft,
              count(*) FILTER (WHERE c.status IN ('EXPIRED', 'RENEWED')
                                 AND c.end_date >= :inicioDoMes
                                 AND c.end_date <= :hoje) AS expired_this_month,
              -- Ativos no fim do mês passado, reconstruídos dos carimbos de
              -- data: não existe tabela de histórico, e não vale a pena criar
              -- uma só para uma seta de porcentagem. Contrato que nasceu e
              -- morreu dentro do mês passado não entra nesta conta.
              count(*) FILTER (WHERE c.activated_at IS NOT NULL
                                 AND c.activated_at < :viradaDoMes
                                 AND c.end_date >= :ultimoDiaDoMesPassado
                                 AND (c.cancelled_at IS NULL
                                      OR c.cancelled_at >= :viradaDoMes)) AS active_last_month
            FROM contracts c
            JOIN clients cl ON cl.id = c.client_id
            WHERE %s
            """.formatted(ESCOPO);

    private static final String RECEITA = """
            WITH escopo AS (
                SELECT cl.id FROM clients cl WHERE %s
            )
            SELECT
              (SELECT COALESCE(sum(p.amount), 0)
                 FROM payments p
                 JOIN billings b ON b.id = p.billing_id
                WHERE b.client_id IN (SELECT id FROM escopo)
                  AND NOT p.refunded
                  AND p.paid_at >= :inicioDoMesTs AND p.paid_at < :viradaDoMesTs) AS realized_month,
              (SELECT COALESCE(sum(b.amount), 0)
                 FROM billings b
                WHERE b.client_id IN (SELECT id FROM escopo)
                  AND b.status <> 'CANCELLED'
                  AND b.due_date >= :inicioDoMes AND b.due_date < :inicioDoProximoMes)
                                                                                AS expected_month,
              (SELECT COALESCE(sum(p.amount), 0)
                 FROM payments p
                 JOIN billings b ON b.id = p.billing_id
                WHERE b.client_id IN (SELECT id FROM escopo)
                  AND NOT p.refunded
                  AND p.paid_at >= :inicioDoAnoTs) AS realized_year,
              (SELECT COALESCE(sum(b.amount), 0)
                 FROM billings b
                WHERE b.client_id IN (SELECT id FROM escopo)
                  AND b.status <> 'CANCELLED'
                  AND b.due_date >= :inicioDoAno AND b.due_date <= :fimDoAno) AS expected_year,
              -- MRR: o valor do contrato distribuído pelos dias de vigência e
              -- normalizado em 30. É aproximação assumida — o contrato guarda o
              -- total do período, não a mensalidade — e serve para a tendência,
              -- que é o que o número responde.
              (SELECT COALESCE(round(sum(c.value_amount * 30.0
                                         / GREATEST(1, c.end_date - c.start_date)), 2), 0)
                 FROM contracts c
                WHERE c.client_id IN (SELECT id FROM escopo)
                  AND c.status = 'ACTIVE') AS mrr
            """.formatted(ESCOPO);

    private static final String TAXA_DE_RENOVACAO = """
            SELECT
              count(*) FILTER (WHERE c.status = 'RENEWED') AS renewed,
              count(*) FILTER (WHERE c.status IN ('EXPIRED', 'CANCELLED')) AS lost
            FROM contracts c
            JOIN clients cl ON cl.id = c.client_id
            WHERE c.end_date BETWEEN :de AND :ate
              AND c.status IN ('RENEWED', 'EXPIRED', 'CANCELLED')
              AND %s
            """.formatted(ESCOPO);

    private static final String INADIMPLENCIA = """
            WITH atrasadas AS (
                SELECT b.client_id,
                       b.due_date,
                       b.amount - COALESCE(pg.pago, 0) AS saldo
                  FROM billings b
                  JOIN clients cl ON cl.id = b.client_id
                  LEFT JOIN LATERAL (
                      SELECT sum(amount) AS pago FROM payments
                       WHERE billing_id = b.id AND NOT refunded) pg ON TRUE
                 WHERE b.status = 'OVERDUE' AND %s
            )
            SELECT count(DISTINCT client_id) AS clientes,
                   COALESCE(sum(saldo), 0) AS total,
                   COALESCE(max(CAST(:hoje AS date) - due_date), 0) AS maior_atraso
            FROM atrasadas
            """.formatted(ESCOPO);

    private static final String SERIE_DE_RECEITA = """
            WITH escopo AS (
                SELECT cl.id FROM clients cl WHERE %s
            ),
            realizada AS (
                SELECT date_trunc('month', p.paid_at) AS mes, sum(p.amount) AS total
                  FROM payments p
                  JOIN billings b ON b.id = p.billing_id
                 WHERE b.client_id IN (SELECT id FROM escopo)
                   AND NOT p.refunded
                   AND p.paid_at >= :deTs AND p.paid_at < :ateTs
                 GROUP BY 1
            ),
            prevista AS (
                SELECT date_trunc('month', b.due_date)::timestamptz AS mes, sum(b.amount) AS total
                  FROM billings b
                 WHERE b.client_id IN (SELECT id FROM escopo)
                   AND b.status <> 'CANCELLED'
                   AND b.due_date >= :de AND b.due_date <= :ate
                 GROUP BY 1
            )
            SELECT to_char(COALESCE(r.mes, p.mes), 'YYYY-MM') AS mes,
                   COALESCE(r.total, 0) AS realizada,
                   COALESCE(p.total, 0) AS prevista
            FROM realizada r
            FULL OUTER JOIN prevista p ON r.mes = p.mes
            ORDER BY 1
            """.formatted(ESCOPO);

    private static final String INADIMPLENTES = """
            SELECT cl.id AS client_id,
                   cl.legal_name,
                   count(b.id) AS cobrancas,
                   sum(b.amount - COALESCE(pg.pago, 0)) AS total,
                   max(CAST(:hoje AS date) - b.due_date) AS maior_atraso
              FROM clients cl
              JOIN billings b ON b.client_id = cl.id AND b.status = 'OVERDUE'
              LEFT JOIN LATERAL (
                  SELECT sum(amount) AS pago FROM payments
                   WHERE billing_id = b.id AND NOT refunded) pg ON TRUE
             WHERE %s
             GROUP BY cl.id, cl.legal_name
             ORDER BY total DESC
             LIMIT :size OFFSET :offset
            """.formatted(ESCOPO);

    private static final String QUANTOS_INADIMPLENTES = """
            SELECT count(DISTINCT cl.id)
              FROM clients cl
              JOIN billings b ON b.client_id = cl.id AND b.status = 'OVERDUE'
             WHERE %s
            """.formatted(ESCOPO);

    private static final String VENCIMENTOS = """
            SELECT CASE
                     WHEN c.end_date - CAST(:hoje AS date) <= 7  THEN '0-7'
                     WHEN c.end_date - CAST(:hoje AS date) <= 15 THEN '8-15'
                     WHEN c.end_date - CAST(:hoje AS date) <= 30 THEN '16-30'
                     ELSE '31+' END AS faixa,
                   count(*) AS quantos,
                   COALESCE(sum(c.value_amount), 0) AS total
              FROM contracts c
              JOIN clients cl ON cl.id = c.client_id
             WHERE c.status = 'ACTIVE'
               AND c.end_date BETWEEN :hoje AND :limite
               AND %s
             GROUP BY 1
             ORDER BY 1
            """.formatted(ESCOPO);

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public ContractCounts contractCounts(DashboardScope scope, LocalDate today) {
        LocalDate inicioDoMes = today.withDayOfMonth(1);

        return jdbc.queryForObject(CONTAGENS, base(scope)
                        .addValue("hoje", today)
                        .addValue("em30", today.plusDays(30))
                        .addValue("inicioDoMes", inicioDoMes)
                        .addValue("ultimoDiaDoMesPassado", inicioDoMes.minusDays(1))
                        .addValue("viradaDoMes", meiaNoite(inicioDoMes)),
                (rs, linha) -> new ContractCounts(
                        rs.getLong("active"),
                        rs.getLong("expiring_30"),
                        rs.getLong("draft"),
                        rs.getLong("expired_this_month"),
                        rs.getLong("active_last_month")));
    }

    @Override
    public RevenueTotals revenueTotals(DashboardScope scope, LocalDate today) {
        LocalDate inicioDoMes = today.withDayOfMonth(1);
        LocalDate inicioDoAno = today.withDayOfYear(1);

        return jdbc.queryForObject(RECEITA, base(scope)
                        .addValue("inicioDoMes", inicioDoMes)
                        .addValue("inicioDoMesTs", meiaNoite(inicioDoMes))
                        .addValue("inicioDoProximoMes", inicioDoMes.plusMonths(1))
                        .addValue("viradaDoMesTs", meiaNoite(inicioDoMes.plusMonths(1)))
                        .addValue("inicioDoAno", inicioDoAno)
                        .addValue("inicioDoAnoTs", meiaNoite(inicioDoAno))
                        .addValue("fimDoAno", inicioDoAno.plusYears(1).minusDays(1)),
                (rs, linha) -> new RevenueTotals(
                        rs.getBigDecimal("realized_month"),
                        rs.getBigDecimal("expected_month"),
                        rs.getBigDecimal("realized_year"),
                        rs.getBigDecimal("expected_year"),
                        rs.getBigDecimal("mrr")));
    }

    @Override
    public RenewalRate renewalRate(DashboardScope scope, LocalDate from, LocalDate until,
                                   String label) {
        return jdbc.queryForObject(TAXA_DE_RENOVACAO, base(scope)
                        .addValue("de", from)
                        .addValue("ate", until),
                (rs, linha) -> RenewalRate.of(label, rs.getLong("renewed"), rs.getLong("lost")));
    }

    @Override
    public DelinquencyTotals delinquency(DashboardScope scope, LocalDate today) {
        return jdbc.queryForObject(INADIMPLENCIA, base(scope).addValue("hoje", today),
                (rs, linha) -> new DelinquencyTotals(
                        rs.getLong("clientes"),
                        rs.getBigDecimal("total"),
                        rs.getLong("maior_atraso")));
    }

    @Override
    public List<RevenuePoint> revenueSeries(DashboardScope scope, LocalDate from, LocalDate until) {
        return jdbc.query(SERIE_DE_RECEITA, base(scope)
                        .addValue("de", from)
                        .addValue("ate", until)
                        .addValue("deTs", meiaNoite(from))
                        .addValue("ateTs", meiaNoite(until.plusDays(1))),
                (rs, linha) -> new RevenuePoint(
                        rs.getString("mes"),
                        rs.getBigDecimal("realizada"),
                        rs.getBigDecimal("prevista")));
    }

    @Override
    public PageResult<DelinquentClient> delinquentClients(DashboardScope scope, LocalDate today,
                                                          int page, int size) {
        Long total = jdbc.queryForObject(QUANTOS_INADIMPLENTES, base(scope), Long.class);

        List<DelinquentClient> conteudo = jdbc.query(INADIMPLENTES, base(scope)
                        .addValue("hoje", today)
                        .addValue("size", size)
                        .addValue("offset", (long) page * size),
                (rs, linha) -> new DelinquentClient(
                        rs.getObject("client_id", UUID.class),
                        rs.getString("legal_name"),
                        rs.getLong("cobrancas"),
                        rs.getBigDecimal("total"),
                        rs.getLong("maior_atraso")));

        return PageResult.of(conteudo, page, size, total == null ? 0 : total);
    }

    @Override
    public List<ExpiringBucket> expiringTimeline(DashboardScope scope, LocalDate today,
                                                 int daysAhead) {
        return jdbc.query(VENCIMENTOS, base(scope)
                        .addValue("hoje", today)
                        .addValue("limite", today.plusDays(daysAhead)),
                (rs, linha) -> new ExpiringBucket(
                        rs.getString("faixa"),
                        rs.getLong("quantos"),
                        rs.getBigDecimal("total")));
    }

    /**
     * Todo parâmetro começa aqui, com o escopo dentro.
     *
     * <p>Uma consulta nova só compila se passar por este método — e aí o escopo
     * já está no lugar. É proteção por construção, não por lembrança.
     */
    private static MapSqlParameterSource base(DashboardScope scope) {
        return new MapSqlParameterSource("scope", scope.accountManagerId());
    }

    /** A coluna é {@code TIMESTAMPTZ}; a comparação tem que ser em UTC. */
    private static OffsetDateTime meiaNoite(LocalDate dia) {
        return dia.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

}
