package com.caiocodes.vigencia.notification.infrastructure.messaging;

import com.caiocodes.vigencia.notification.application.NotificationFormats;
import com.caiocodes.vigencia.notification.application.ScheduleNotificationUseCase;
import com.caiocodes.vigencia.notification.application.ScheduleNotificationUseCase.NotificationRequest;
import com.caiocodes.vigencia.notification.domain.NotificationType;
import com.caiocodes.vigencia.shared.infrastructure.messaging.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Reage aos eventos de contrato.
 *
 * <p>Hoje só a renovação vira aviso — os outros eventos de contrato passam pela
 * fila e são descartados. É de propósito: o binding é {@code contract.*} para
 * que um evento novo não exija mexer na topologia, e é aqui, num lugar só, que
 * se decide o que merece e-mail.
 *
 * <p><b>Sobre entrega ao menos uma vez:</b> este consumidor pode receber o mesmo
 * evento duas vezes. Não há {@code if (jaProcessei)} nenhum — o {@code eventId}
 * vai no payload e o índice único {@code uk_notif_event} recusa o segundo.
 * Idempotência que depende de o programador lembrar de escrevê-la é
 * idempotência que a próxima refatoração remove sem ninguém perceber.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractEventListener {

    private final ScheduleNotificationUseCase schedule;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitTopology.QUEUE_CONTRACT)
    public void onContractEvent(Message message) {
        EventEnvelope evento = EventEnvelope.of(message, objectMapper);
        if (!evento.is("contract.renewed")) {
            log.debug("notification.evento_ignorado eventType={}", evento.eventType());
            return;
        }

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("eventId", evento.eventId());
        dados.put("previousNumber", evento.text("previousNumber"));
        dados.put("contractNumber", evento.text("newNumber"));
        dados.put("startDateFormatted", NotificationFormats.date(evento.date("startDate")));
        dados.put("endDateFormatted", NotificationFormats.date(evento.date("endDate")));
        dados.put("amountFormatted", NotificationFormats.money(
                evento.decimal("amount"), evento.text("currency")));

        schedule.execute(new NotificationRequest(
                NotificationType.CONTRACT_RENEWED,
                evento.uuid("clientId"),
                evento.uuid("newContractId"),
                null,
                null,
                "Contrato renovado: " + evento.text("newNumber"),
                dados,
                true));
    }
}
