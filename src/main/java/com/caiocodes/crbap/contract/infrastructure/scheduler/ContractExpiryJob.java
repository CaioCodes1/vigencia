package com.caiocodes.crbap.contract.infrastructure.scheduler;

import com.caiocodes.crbap.contract.application.ExpireContractsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A varredura diária de vencimentos.
 *
 * <p>O job é <b>casca</b>: agenda, chama o caso de uso e registra o resultado.
 * Nenhuma regra mora aqui de propósito — regra dentro de {@code @Scheduled} só
 * é testável esperando o relógio, e é assim que se chega a um sistema onde
 * "renovação automática" ninguém sabe explicar.
 *
 * <p>Roda às 02:00 no fuso de São Paulo: depois da virada do dia (senão um
 * contrato que vence hoje seria expirado hoje mesmo) e antes do horário
 * comercial, para que o painel já esteja correto quando alguém abrir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crbap.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ContractExpiryJob {

    private final ExpireContractsUseCase expireContracts;

    @Scheduled(cron = "${crbap.jobs.expiry-cron:0 0 2 * * *}", zone = "America/Sao_Paulo")
    public void run() {
        long inicio = System.nanoTime();
        int expirados = expireContracts.execute();
        log.info("job.expiry_scan expirados={} duracaoMs={}",
                expirados, (System.nanoTime() - inicio) / 1_000_000);
    }
}
