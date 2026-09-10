package com.caiocodes.crbap.notification.application;

import com.caiocodes.crbap.notification.application.port.NotificationRenderer;
import com.caiocodes.crbap.notification.application.port.NotificationSender;
import com.caiocodes.crbap.notification.application.port.NotificationSender.RenderedNotification;
import com.caiocodes.crbap.notification.application.port.NotificationSender.SendResult;
import com.caiocodes.crbap.notification.domain.Notification;
import com.caiocodes.crbap.notification.domain.NotificationChannel;
import com.caiocodes.crbap.notification.domain.NotificationId;
import com.caiocodes.crbap.notification.domain.NotificationRepository;
import com.caiocodes.crbap.shared.application.BusinessMetrics;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envia <b>um</b> aviso, em transação própria.
 *
 * <p>Classe separada do {@code DispatchNotificationsUseCase} pelo mesmo motivo
 * do {@code ContractExpirer}: {@code @Transactional} funciona por proxy, e um
 * método chamando outro da mesma classe não passa por ele — o
 * {@code REQUIRES_NEW} seria ignorado em silêncio e o lote voltaria a
 * compartilhar uma transação.
 *
 * <p>O mapa de {@code senders} por canal é o que faz o "O" de SOLID valer:
 * acrescentar SMS é registrar um bean novo, sem tocar nesta classe.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private final NotificationRepository notifications;
    private final NotificationRenderer renderer;
    private final Map<NotificationChannel, NotificationSender> senders;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public NotificationDispatcher(NotificationRepository notifications,
                                  NotificationRenderer renderer,
                                  List<NotificationSender> senders,
                                  BusinessMetrics metrics,
                                  Clock clock) {
        this.notifications = notifications;
        this.renderer = renderer;
        this.metrics = metrics;
        this.clock = clock;
        // Se dois beans declararem o mesmo canal, o contexto se recusa a subir.
        // É o que se quer: dois remetentes de e-mail vivos ao mesmo tempo é uma
        // ambiguidade que se descobre com o cliente reclamando de e-mail
        // duplicado.
        this.senders = senders.stream().collect(
                Collectors.toMap(NotificationSender::channel, Function.identity()));
    }

    /** @return true se o aviso saiu */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean dispatch(NotificationId id) {
        Notification aviso = notifications.findById(id).orElse(null);
        if (aviso == null || !aviso.isPending()) {
            // Outra instância pegou primeiro, ou o aviso foi cancelado entre a
            // consulta e agora. Caso normal, não erro.
            return false;
        }

        NotificationSender sender = senders.get(aviso.channel());
        if (sender == null) {
            aviso.markAttemptFailed("Nenhum remetente configurado para " + aviso.channel(), true);
            notifications.save(aviso);
            log.error("notification.sem_remetente canal={} notificationId={}",
                    aviso.channel(), id);
            return false;
        }

        SendResult resultado = enviar(sender, aviso);
        metrics.notificationSent(aviso.type().name(), aviso.channel().name(),
                resultado.success());
        if (resultado.success()) {
            aviso.markSent(clock.instant());
            notifications.save(aviso);
            log.info("notification.sent tipo={} para={} tentativa={}",
                    aviso.type(), aviso.recipient(), aviso.attempts());
            return true;
        }

        boolean desistiu = aviso.markAttemptFailed(resultado.error(), resultado.permanent());
        notifications.save(aviso);
        log.warn("notification.falhou tipo={} para={} tentativa={} permanente={} desistiu={}",
                aviso.type(), aviso.recipient(), aviso.attempts(), resultado.permanent(),
                desistiu);
        return false;
    }

    /**
     * A renderização entra no mesmo tratamento de erro do envio.
     *
     * <p>Template quebrado é falha <b>permanente</b>: tentar de novo daqui a um
     * minuto renderiza o mesmo template quebrado. Sem esta distinção, um erro de
     * template consumiria as quatro tentativas de todos os avisos daquele tipo.
     */
    private SendResult enviar(NotificationSender sender, Notification aviso) {
        String corpo;
        try {
            corpo = renderer.render(aviso);
        } catch (RuntimeException e) {
            log.error("notification.render.falhou tipo={} notificationId={}",
                    aviso.type(), aviso.id(), e);
            return SendResult.permanentFailure("Falha ao renderizar: " + e.getMessage());
        }
        return sender.send(new RenderedNotification(aviso.recipient(), aviso.subject(), corpo));
    }
}
