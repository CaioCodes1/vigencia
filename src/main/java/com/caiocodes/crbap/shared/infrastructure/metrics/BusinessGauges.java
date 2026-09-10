package com.caiocodes.crbap.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Os números de estado do negócio, atualizados de um em um minuto.
 *
 * <p><b>Por que uma cópia em memória, e não a consulta dentro do gauge:</b> o
 * jeito que aparece nos exemplos é
 * {@code Gauge.builder("...", repo, Repo::countActive)} — e aí a consulta roda a
 * cada <i>scrape</i> do Prometheus, de 15 em 15 segundos, para sempre. São
 * quatro agregações sobre as tabelas principais, quatro vezes por minuto, por
 * instância, para um número que muda algumas vezes por dia. Um alvo de
 * monitoramento que sozinho pesa no banco tende a ser desligado no primeiro
 * incidente — que é exatamente quando ele é necessário.
 *
 * <p>Aqui a tarefa agendada atualiza `AtomicLong`s e o gauge lê memória. O custo
 * fica fixo (quatro consultas por minuto) e independente de quantos Prometheus
 * apontarem para cá.
 *
 * <p><b>Sem {@code @SchedulerLock}, de propósito.</b> Terceira vez que essa
 * distinção aparece no projeto: a trava serve para <i>excluir</i> (só uma
 * instância deve mandar o e-mail). Aqui, cada instância publica os próprios
 * números e o Prometheus raspa todas — com a trava, duas das três instâncias
 * reportariam zero para sempre, e o gráfico mostraria uma empresa sem contratos.
 *
 * <p>Consulta por JDBC e não pelos repositórios de domínio: contagem para
 * monitoramento não é caso de uso, e acrescentar {@code countActive()} a três
 * interfaces de domínio para servir a um detalhe de operação inverteria a
 * dependência que a arquitetura inteira protege.
 */
@Slf4j
@Component
public class BusinessGauges {

    private static final String CONTRATOS_ATIVOS =
            "SELECT count(*) FROM contracts WHERE status = 'ACTIVE'";

    private static final String VENCENDO_EM_30 = """
            SELECT count(*) FROM contracts
             WHERE status = 'ACTIVE'
               AND end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30
            """;

    /** O saldo, não o valor cheio: parte já pode ter sido paga. */
    private static final String VALOR_EM_ATRASO = """
            SELECT COALESCE(sum(b.amount - COALESCE(pg.pago, 0)), 0)
              FROM billings b
              LEFT JOIN LATERAL (
                  SELECT sum(amount) AS pago FROM payments
                   WHERE billing_id = b.id AND NOT refunded) pg ON TRUE
             WHERE b.status = 'OVERDUE'
            """;

    private static final String OUTBOX_PENDENTE =
            "SELECT count(*) FROM outbox_events WHERE published_at IS NULL";

    private final JdbcTemplate jdbc;
    private final MeterRegistry registry;

    private final AtomicLong contratosAtivos = new AtomicLong();
    private final AtomicLong vencendoEm30Dias = new AtomicLong();
    private final AtomicLong valorEmAtrasoCentavos = new AtomicLong();
    private final AtomicLong outboxPendente = new AtomicLong();

    public BusinessGauges(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    @PostConstruct
    void registrar() {
        gauge("crbap.contracts.active", "Contratos ativos", contratosAtivos, null);
        gauge("crbap.contracts.expiring_30d",
                "Contratos ativos que vencem nos próximos 30 dias", vencendoEm30Dias, null);
        // Guardado em centavos (inteiro, sem deriva de ponto flutuante) e
        // exposto em reais, que e a unidade que o painel e o alerta usam.
        gauge("crbap.billings.overdue.amount", "Saldo total em atraso",
                valorEmAtrasoCentavos, "BRL", centavos -> centavos.doubleValue() / 100);
        gauge("crbap.outbox.pending",
                "Eventos aguardando publicação — se subir e não cair, nada está saindo",
                outboxPendente, null);
        atualizar();
    }

    /**
     * Falha ao medir não pode derrubar nada.
     *
     * <p>Banco indisponível já vai aparecer no health check e em toda requisição.
     * O gauge fica com o último valor conhecido — e a ausência de atualização é
     * detectável pelo próprio Prometheus, que enxerga a série parada.
     */
    @Scheduled(fixedDelayString = "${crbap.jobs.metrics-refresh-delay:60000}")
    public void atualizar() {
        try {
            contratosAtivos.set(contar(CONTRATOS_ATIVOS));
            vencendoEm30Dias.set(contar(VENCENDO_EM_30));
            valorEmAtrasoCentavos.set(emCentavos());
            outboxPendente.set(contar(OUTBOX_PENDENTE));
        } catch (RuntimeException e) {
            log.warn("metrics.refresh_falhou: {}", e.getMessage());
        }
    }

    private void gauge(String nome, String descricao, AtomicLong valor, String unidade) {
        gauge(nome, descricao, valor, unidade, AtomicLong::doubleValue);
    }

    private void gauge(String nome, String descricao, AtomicLong valor, String unidade,
                       ToDoubleFunction<AtomicLong> leitura) {
        Gauge.Builder<AtomicLong> builder = Gauge.builder(nome, valor, leitura)
                .description(descricao);
        if (unidade != null) {
            builder.baseUnit(unidade);
        }
        builder.register(registry);
    }

    private long contar(String sql) {
        Long total = jdbc.queryForObject(sql, Long.class);
        return total == null ? 0 : total;
    }

    private long emCentavos() {
        BigDecimal total = jdbc.queryForObject(VALOR_EM_ATRASO, BigDecimal.class);
        return total == null ? 0 : total.movePointRight(2).longValue();
    }
}
