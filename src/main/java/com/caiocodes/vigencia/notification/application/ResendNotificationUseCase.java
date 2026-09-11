package com.caiocodes.vigencia.notification.application;

import com.caiocodes.vigencia.notification.domain.Notification;
import com.caiocodes.vigencia.notification.domain.NotificationId;
import com.caiocodes.vigencia.notification.domain.NotificationRepository;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reenvio manual (RF-25).
 *
 * <p>Devolve o aviso para {@code PENDING} e o job de envio faz o resto — não
 * manda o e-mail aqui dentro. Manter um caminho só de envio é o que garante que
 * o reenvio grave tentativa, erro e horário do mesmo jeito que o envio
 * automático; um segundo caminho seria um segundo lugar para o log ficar
 * incompleto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResendNotificationUseCase {

    private final NotificationRepository notifications;

    @Transactional
    public NotificationView execute(NotificationId id) {
        Notification aviso = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Notificação", id));

        aviso.retry();
        Notification salvo = notifications.save(aviso);
        log.info("notification.resend notificationId={} tipo={} para={}",
                id, salvo.type(), salvo.recipient());
        return NotificationView.from(salvo);
    }
}
