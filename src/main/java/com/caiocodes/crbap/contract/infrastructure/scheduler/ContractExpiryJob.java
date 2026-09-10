package com.caiocodes.crbap.contract.infrastructure.scheduler;

import com.caiocodes.crbap.contract.application.AutoRenewContractsUseCase;
import com.caiocodes.crbap.contract.application.ExpireContractsUseCase;
import com.caiocodes.crbap.notification.application.ScanExpiringContractsUseCase;
import com.caiocodes.crbap.shared.application.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A varredura diária de contratos: renova, expira e avisa — <b>nesta ordem</b>.
 *
 * <p>A ordem é regra de negócio, não conveniência de código:
 *
 * <ol>
 *   <li><b>Renovar primeiro.</b> Um contrato com renovação automática nunca
 *       deve chegar a {@code EXPIRED}. Invertido, o cliente receberia "seu
 *       contrato venceu" e, um minuto depois, "seu contrato foi renovado".</li>
 *   <li><b>Expirar depois.</b> Sobra só o que não tinha renovação
 *       automática.</li>
 *   <li><b>Avisar por último.</b> A régua olha o estado <i>final</i> do dia: o
 *       que acabou de virar {@code EXPIRED} entra no aviso D+1 de amanhã, e o
 *       que foi renovado não entra em aviso nenhum.</li>
 * </ol>
 *
 * <p>O job é <b>casca</b>: agenda, chama três casos de uso e registra. Nenhuma
 * regra mora aqui — regra dentro de {@code @Scheduled} só é testável esperando
 * o relógio.
 *
 * <p>Roda às 03:00 no fuso de São Paulo: depois da virada do dia (senão um
 * contrato que vence hoje seria expirado hoje mesmo) e antes do horário
 * comercial, para que o painel já esteja correto quando alguém abrir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crbap.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ContractExpiryJob {

    private final AutoRenewContractsUseCase autoRenew;
    private final ExpireContractsUseCase expireContracts;
    private final ScanExpiringContractsUseCase scanExpiring;
    private final BusinessMetrics metrics;

    /**
     * {@code lockAtLeastFor} de 5 min mesmo que o job leve segundos: sem ele,
     * com relógios levemente diferentes entre as instâncias, a segunda pega a
     * trava logo depois e manda tudo de novo.
     */
    @Scheduled(cron = "${crbap.jobs.expiry-cron:0 0 3 * * *}", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "contract-expiration-scan",
            lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void run() {
        long inicio = System.nanoTime();

        int renovados = autoRenew.execute();
        int expirados = expireContracts.execute();
        int avisos = scanExpiring.execute();

        metrics.jobSucceeded("contract-expiration-scan");
        log.info("job.contract_scan renovados={} expirados={} avisos={} duracaoMs={}",
                renovados, expirados, avisos, (System.nanoTime() - inicio) / 1_000_000);
    }
}
