package com.caiocodes.crbap.notification.infrastructure.sender;

import com.caiocodes.crbap.notification.application.port.NotificationSender;
import com.caiocodes.crbap.notification.domain.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Remetente de desenvolvimento: escreve o e-mail no log em vez de mandar.
 *
 * <p>Não é preguiça — é o que evita o acidente mais caro de um sistema de
 * notificação: subir com um dump de produção em desenvolvimento e disparar
 * quinhentos e-mails de "seu contrato vence" para clientes de verdade.
 *
 * <p>É o padrão (`matchIfMissing = true`). Para mandar de verdade,
 * `crbap.notification.smtp-enabled=true` e as credenciais do SMTP.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "crbap.notification.smtp-enabled", havingValue = "false",
        matchIfMissing = true)
public class LogNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public SendResult send(RenderedNotification notification) {
        log.info("""
                notification.log_sender
                  para: {}
                  assunto: {}
                {}""", notification.recipient(), notification.subject(), notification.body());
        return SendResult.ok();
    }
}
