package com.caiocodes.crbap.notification.infrastructure.persistence;

import com.caiocodes.crbap.notification.domain.Notification;
import com.caiocodes.crbap.notification.domain.NotificationChannel;
import com.caiocodes.crbap.notification.domain.NotificationId;
import com.caiocodes.crbap.notification.domain.NotificationState;
import com.caiocodes.crbap.notification.domain.NotificationStatus;
import com.caiocodes.crbap.notification.domain.NotificationType;
import com.caiocodes.crbap.notification.infrastructure.persistence.NotificationEntity.NotificationChannelValue;
import com.caiocodes.crbap.notification.infrastructure.persistence.NotificationEntity.NotificationStatusValue;
import com.caiocodes.crbap.notification.infrastructure.persistence.NotificationEntity.NotificationTypeValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Tradução entre o agregado Notificação e o modelo de persistência. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPersistenceMapper {

    private static final TypeReference<Map<String, Object>> PAYLOAD =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public Notification toDomain(NotificationEntity entity) {
        return Notification.rehydrate(new NotificationState(
                NotificationId.of(entity.getId()),
                NotificationType.valueOf(entity.getType().name()),
                NotificationChannel.valueOf(entity.getChannel().name()),
                entity.getClientId(),
                entity.getContractId(),
                entity.getBillingId(),
                entity.getDaysOffset() == null ? null : entity.getDaysOffset().intValue(),
                entity.getRecipient(),
                entity.getSubject(),
                deserialize(entity.getPayload()),
                NotificationStatus.valueOf(entity.getStatus().name()),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getScheduledAt(),
                entity.getSentAt()));
    }

    public void copyToEntity(Notification notification, NotificationEntity entity) {
        entity.setId(notification.id().value());
        entity.setType(NotificationTypeValue.valueOf(notification.type().name()));
        entity.setChannel(NotificationChannelValue.valueOf(notification.channel().name()));
        entity.setClientId(notification.clientId());
        entity.setContractId(notification.contractId());
        entity.setBillingId(notification.billingId());
        entity.setDaysOffset(notification.daysOffset() == null ? null
                : notification.daysOffset().shortValue());
        entity.setRecipient(notification.recipient());
        entity.setSubject(notification.subject());
        entity.setPayload(serializePayload(notification.payload()));
        entity.setStatus(NotificationStatusValue.valueOf(notification.status().name()));
        entity.setAttempts((short) notification.attempts());
        entity.setLastError(notification.lastError());
        entity.setScheduledAt(notification.scheduledAt());
        entity.setSentAt(notification.sentAt());
    }

    /** Público porque o adaptador insere o payload por SQL nativo. */
    public String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Payload que não serializa é bug de programação. Falhar aqui é
            // melhor que gravar o aviso sem o conteúdo e descobrir na hora do
            // envio, com o template renderizando vazio.
            throw new IllegalStateException("Não foi possível serializar o payload do aviso", e);
        }
    }

    /**
     * Payload ilegível não derruba a leitura.
     *
     * <p>É o oposto da gravação de propósito: na escrita, falhar cedo; na
     * leitura, degradar. A tela "vocês me avisaram?" precisa mostrar que o aviso
     * saiu, com data e destinatário, mesmo que o conteúdo tenha ficado
     * corrompido por uma migração mal feita anos atrás.
     */
    private Map<String, Object> deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, PAYLOAD);
        } catch (JsonProcessingException e) {
            log.warn("notification.payload_ilegivel: {}", e.getMessage());
            return Map.of();
        }
    }
}
