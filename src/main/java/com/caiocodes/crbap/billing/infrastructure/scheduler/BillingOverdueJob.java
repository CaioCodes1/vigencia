package com.caiocodes.crbap.billing.infrastructure.scheduler;

import com.caiocodes.crbap.billing.application.MarkOverdueBillingsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A varredura diária de inadimplência.
 *
 * <p>Roda às 02:30, meia hora <b>depois</b> da varredura de vencimentos de
 * contrato. A ordem importa: um contrato que expira hoje tem cobranças que
 * também vencem hoje, e marcar a cobrança antes de o contrato expirar deixaria
 * as duas telas discordando durante meia manhã.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crbap.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BillingOverdueJob {

    private final MarkOverdueBillingsUseCase markOverdue;

    @Scheduled(cron = "${crbap.jobs.overdue-cron:0 30 2 * * *}", zone = "America/Sao_Paulo")
    public void run() {
        long inicio = System.nanoTime();
        int marcadas = markOverdue.execute();
        log.info("job.overdue_scan marcadas={} duracaoMs={}",
                marcadas, (System.nanoTime() - inicio) / 1_000_000);
    }
}
