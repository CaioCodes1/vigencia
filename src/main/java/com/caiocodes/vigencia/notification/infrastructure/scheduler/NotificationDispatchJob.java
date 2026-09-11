package com.caiocodes.vigencia.notification.infrastructure.scheduler;

import com.caiocodes.vigencia.notification.application.DispatchNotificationsUseCase;
import com.caiocodes.vigencia.shared.application.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manda o que está agendado, a cada minuto.
 *
 * <p>Separado da varredura de propósito: agendar e enviar falham por motivos
 * diferentes. O SMTP cair não pode impedir o sistema de <b>saber</b> quem
 * precisa ser avisado — os avisos ficam gravados como {@code PENDING} e saem
 * quando o provedor voltar.
 *
 * <p>{@code fixedDelay}, e não {@code fixedRate}: com {@code fixedRate}, uma
 * rodada que demorasse mais de um minuto dispararia a seguinte por cima. Aqui a
 * próxima só começa um minuto depois de a anterior <b>terminar</b>.
 *
 * <p>A trava é curta ({@code lockAtMostFor} de 5 min) porque o job é curto: se
 * a instância morrer no meio, outra assume em cinco minutos em vez de meia
 * hora — e aviso atrasado meia hora já é aviso ruim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vigencia.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatchJob {

    private final DispatchNotificationsUseCase dispatch;
    private final BusinessMetrics metrics;

    @Scheduled(fixedDelayString = "${vigencia.jobs.notification-dispatch-delay:60000}")
    @SchedulerLock(name = "notification-dispatch",
            lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void run() {
        int enviados = dispatch.execute();
        metrics.jobSucceeded("notification-dispatch");
        if (enviados > 0) {
            log.info("job.notification_dispatch enviados={}", enviados);
        }
    }
}
