package com.caiocodes.crbap.notification.application;

import com.caiocodes.crbap.notification.domain.NotificationId;
import com.caiocodes.crbap.notification.domain.NotificationRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Manda o que está agendado.
 *
 * <p>Roda a cada minuto e trabalha em lote. <b>Não</b> é transacional: o método
 * percorre a lista e cada aviso é enviado na sua própria transação, pelo
 * {@link NotificationDispatcher}. Uma transação para o lote inteiro seguraria
 * conexão do pool pelo tempo de N chamadas de rede — e uma falha de SMTP no
 * décimo aviso desfaria os nove que já saíram, que é pior do que não ter
 * mandado: o e-mail foi entregue e o banco diz que não.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchNotificationsUseCase {

    private static final int BATCH_SIZE = 50;

    private final NotificationRepository notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    /** @return quantos avisos saíram nesta rodada */
    public int execute() {
        List<NotificationId> pendentes =
                notifications.findPendingUntil(clock.instant(), BATCH_SIZE);
        if (pendentes.isEmpty()) {
            return 0;
        }

        int enviados = 0;
        for (NotificationId id : pendentes) {
            try {
                if (dispatcher.dispatch(id)) {
                    enviados++;
                }
            } catch (RuntimeException e) {
                // Erro que a implementação do canal não previu. O aviso fica
                // PENDING e volta na próxima rodada — não pode derrubar o lote.
                log.error("notification.dispatch.falhou notificationId={}", id, e);
            }
        }
        log.info("notification.dispatch lote={} enviados={}", pendentes.size(), enviados);
        return enviados;
    }
}
