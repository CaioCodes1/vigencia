package com.caiocodes.crbap.notification.infrastructure.messaging;

import com.caiocodes.crbap.notification.application.NotificationFormats;
import com.caiocodes.crbap.notification.application.ScheduleNotificationUseCase;
import com.caiocodes.crbap.notification.application.ScheduleNotificationUseCase.NotificationRequest;
import com.caiocodes.crbap.notification.domain.NotificationType;
import com.caiocodes.crbap.shared.infrastructure.messaging.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Reage aos eventos de cobrança: atraso e recibo de pagamento.
 *
 * <p><b>{@code billing.issued} não vira e-mail.</b> Um contrato anual mensal
 * emite doze cobranças na ativação, e mandar doze e-mails de uma vez é a
 * definição de spam — o cliente marca como lixo e deixa de receber os avisos que
 * importam. O aviso de cobrança nova entra quando existir a régua D-3 do
 * vencimento, não na emissão.
 *
 * <p>As duas deduplicações são diferentes de propósito:
 * <ul>
 *   <li><b>Atraso</b> usa a janela — {@code uk_notif_billing_window} impede
 *       "vencida há 7 dias" de sair duas vezes;</li>
 *   <li><b>Recibo</b> usa o {@code eventId} — {@code uk_notif_event} impede a
 *       redelivery do mesmo PIX de virar dois recibos, mas deixa dois pagamentos
 *       parciais gerarem dois recibos, que é o correto.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventListener {

    private final ScheduleNotificationUseCase schedule;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitTopology.QUEUE_BILLING)
    public void onBillingEvent(Message message) {
        EventEnvelope evento = EventEnvelope.of(message, objectMapper);

        if (evento.is("billing.overdue")) {
            avisarAtraso(evento);
        } else if (evento.is("billing.payment_registered")) {
            avisarPagamento(evento);
        } else {
            log.debug("notification.evento_ignorado eventType={}", evento.eventType());
        }
    }

    private void avisarAtraso(EventEnvelope evento) {
        long diasAtraso = evento.number("daysLate");
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("eventId", evento.eventId());
        dados.put("reference", evento.text("reference"));
        dados.put("dueDateFormatted", NotificationFormats.date(evento.date("dueDate")));
        dados.put("remainingFormatted", NotificationFormats.money(
                evento.decimal("remaining"), evento.text("currency")));
        dados.put("daysLate", diasAtraso);

        schedule.execute(new NotificationRequest(
                NotificationType.BILLING_OVERDUE,
                evento.uuid("clientId"),
                null,
                evento.uuid("aggregateId"),
                // A janela negativa é o que impede o aviso de repetir: o mesmo
                // "vencida há 7 dias" só cabe uma vez por cobrança.
                (int) -diasAtraso,
                "Cobrança %s em atraso há %d dia(s)"
                        .formatted(evento.text("reference"), diasAtraso),
                dados,
                true));
    }

    private void avisarPagamento(EventEnvelope evento) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("eventId", evento.eventId());
        dados.put("reference", evento.text("reference"));
        dados.put("method", evento.text("method"));
        dados.put("amountFormatted", NotificationFormats.money(
                evento.decimal("amount"), evento.text("currency")));
        dados.put("remainingFormatted", NotificationFormats.money(
                evento.decimal("remaining"), evento.text("currency")));
        dados.put("settled", evento.flag("settled"));

        schedule.execute(new NotificationRequest(
                NotificationType.PAYMENT_RECEIVED,
                evento.uuid("clientId"),
                null,
                evento.uuid("aggregateId"),
                null,
                "Pagamento recebido",
                dados,
                // Recibo é do cliente. O gestor não precisa de um e-mail a cada
                // pagamento — ele tem o painel para isso.
                false));
    }
}
