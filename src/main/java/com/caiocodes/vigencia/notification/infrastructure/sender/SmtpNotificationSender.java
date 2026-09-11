package com.caiocodes.vigencia.notification.infrastructure.sender;

import com.caiocodes.vigencia.notification.application.port.NotificationSender;
import com.caiocodes.vigencia.notification.domain.NotificationChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Remetente de produção: SMTP.
 *
 * <p>Todo o cuidado desta classe está em <b>classificar o erro</b>. Retentar
 * quatro vezes um e-mail que não existe não conserta o e-mail, ocupa a fila e
 * ainda queima reputação do domínio junto ao provedor. A regra é sempre a
 * mesma: <b>retry só para o que pode dar certo depois</b>.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vigencia.notification.smtp-enabled", havingValue = "true")
public class SmtpNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationSender(JavaMailSender mailSender,
                                  @Value("${vigencia.notification.from:nao-responda@vigencia.local}")
                                  String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public SendResult send(RenderedNotification notification) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(notification.recipient());
            helper.setSubject(notification.subject());
            helper.setText(notification.body(), true);
            mailSender.send(mensagem);
            return SendResult.ok();

        } catch (MessagingException | MailParseException e) {
            // Endereço malformado ou cabeçalho inválido: tentar de novo daqui a
            // um minuto monta exatamente a mesma mensagem inválida.
            return SendResult.permanentFailure("Mensagem inválida: " + e.getMessage());

        } catch (MailAuthenticationException e) {
            // Credencial errada. É transitório no sentido operacional — alguém
            // conserta a configuração e as tentativas seguintes passam.
            log.error("notification.smtp.auth_falhou", e);
            return SendResult.transientFailure("Falha de autenticação no SMTP");

        } catch (MailSendException e) {
            // Servidor fora do ar, timeout, caixa cheia. Vale tentar de novo.
            return SendResult.transientFailure("SMTP indisponível: " + e.getMessage());

        } catch (RuntimeException e) {
            log.error("notification.smtp.erro_inesperado para={}", notification.recipient(), e);
            return SendResult.transientFailure("Erro inesperado: " + e.getMessage());
        }
    }
}
