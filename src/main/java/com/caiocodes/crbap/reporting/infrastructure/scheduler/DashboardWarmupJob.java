package com.caiocodes.crbap.reporting.infrastructure.scheduler;

import com.caiocodes.crbap.reporting.application.DashboardCache;
import com.caiocodes.crbap.reporting.application.DashboardScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém o painel da empresa quente no Redis.
 *
 * <p><b>O problema que ele resolve é o <i>cache stampede</i>.</b> Quando uma
 * chave popular expira às 8h59, as vinte pessoas que abrem o painel às 9h vão
 * todas ao banco fazer a mesma agregação pesada, ao mesmo tempo. O cache, que
 * existe para proteger o banco, escolhe o pior momento possível para deixar de
 * proteger.
 *
 * <p>Recalculando a cada 4 minutos uma chave que vale 5, a janela fria
 * praticamente não existe.
 *
 * <p><b>Só a chave global é aquecida.</b> As chaves por carteira são uma por
 * vendedor e envelhecem sozinhas; aquecer todas seria fazer, de quatro em
 * quatro minutos, uma consulta por pessoa da equipe comercial — trocar um pico
 * ocasional por carga constante.
 *
 * <p><b>Por que ShedLock aqui, e não no relay da outbox:</b> são travas de
 * naturezas opostas. O relay <i>divide</i> trabalho entre as instâncias
 * ({@code FOR UPDATE SKIP LOCKED} particiona a fila). Este job faz a mesma
 * conta para todas elas, e o Redis é compartilhado: a segunda instância só
 * repetiria a agregação para gravar o mesmo valor. Aqui a trava exclui, e é
 * isso que se quer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crbap.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DashboardWarmupJob {

    private final DashboardCache cache;

    @Scheduled(fixedDelayString = "${crbap.jobs.dashboard-warmup-delay:240000}")
    @SchedulerLock(name = "dashboard-cache-warmup",
            lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void run() {
        long inicio = System.nanoTime();
        try {
            cache.refreshSummary(DashboardScope.all());
            log.debug("job.dashboard_warmup duracaoMs={}", (System.nanoTime() - inicio) / 1_000_000);
        } catch (RuntimeException e) {
            // Aquecimento é conforto, não correção: falhou, a próxima
            // requisição calcula na hora. Deixar a exceção subir só encheria o
            // log de erro do agendador de quatro em quatro minutos.
            log.warn("job.dashboard_warmup_falhou: {}", e.getMessage());
        }
    }
}
