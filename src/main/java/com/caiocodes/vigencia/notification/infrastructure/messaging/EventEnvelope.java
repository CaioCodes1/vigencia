package com.caiocodes.vigencia.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

/**
 * Leitura defensiva do evento que chegou pela fila.
 *
 * <p>Existe para que os consumidores não repitam a mesma dúzia de linhas de
 * {@code jsonNode.get(...).isNull() ? null : ...} — e, principalmente, para que
 * a decisão sobre <b>payload ilegível</b> seja tomada num lugar só.
 *
 * <p>Essa decisão é: vai <b>direto para a DLQ</b>, sem retry.
 * {@code AmqpRejectAndDontRequeueException} é o que impede o
 * <i>poison message loop</i> — com requeue, uma mensagem permanentemente ruim
 * volta para a fila, falha, volta, e consome 100% da CPU num laço infinito. É
 * um dos jeitos mais rápidos de derrubar um sistema com mensageria.
 */
final class EventEnvelope {

    private final JsonNode corpo;
    private final String eventId;
    private final String eventType;

    private EventEnvelope(JsonNode corpo, String eventId, String eventType) {
        this.corpo = corpo;
        this.eventId = eventId;
        this.eventType = eventType;
    }

    static EventEnvelope of(Message message, ObjectMapper objectMapper) {
        try {
            JsonNode corpo = objectMapper.readTree(message.getBody());
            String id = message.getMessageProperties().getMessageId();
            Object tipo = message.getMessageProperties().getHeader("eventType");
            return new EventEnvelope(corpo, id, tipo == null ? null : tipo.toString());
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Payload de evento ilegível", e);
        }
    }

    /** O id da mensagem é o id do evento — é a chave de deduplicação. */
    String eventId() {
        return eventId;
    }

    String eventType() {
        return eventType;
    }

    boolean is(String tipo) {
        return tipo.equals(eventType);
    }

    String text(String campo) {
        JsonNode no = corpo.get(campo);
        return no == null || no.isNull() ? null : no.asText();
    }

    UUID uuid(String campo) {
        String valor = text(campo);
        return valor == null ? null : UUID.fromString(valor);
    }

    BigDecimal decimal(String campo) {
        JsonNode no = corpo.get(campo);
        return no == null || no.isNull() ? null : no.decimalValue();
    }

    LocalDate date(String campo) {
        String valor = text(campo);
        return valor == null ? null : LocalDate.parse(valor);
    }

    long number(String campo) {
        JsonNode no = corpo.get(campo);
        return no == null || no.isNull() ? 0L : no.asLong();
    }

    boolean flag(String campo) {
        JsonNode no = corpo.get(campo);
        return no != null && no.asBoolean();
    }
}
