package com.caiocodes.vigencia.notification.domain;

import com.caiocodes.vigencia.shared.domain.AggregateRoot;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Um aviso agendado, e o registro de que ele saiu.
 *
 * <p>A tabela é o log de envio (RF-24): canal, destinatário, conteúdo, tentativa
 * e erro. É o que responde a pergunta que a planilha nunca respondeu —
 * <i>"vocês me avisaram?"</i> — com data, hora e conteúdo.
 *
 * <p><b>O agregado não decide se deve avisar.</b> Quem calcula a janela é o
 * scheduler, que sabe a data de hoje; aqui só se registra o agendamento. E não
 * existe defesa em Java contra o aviso duplicado: essa é do índice único
 * {@code (contract_id, type, days_offset, recipient)}, porque um {@code SELECT}
 * antes do {@code INSERT} não sobrevive a duas instâncias rodando o job ao
 * mesmo tempo.
 */
public class Notification extends AggregateRoot<NotificationId> {

    /** Depois disso, desistir e marcar FAILED. */
    public static final int MAX_ATTEMPTS = 4;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_ERROR = 1000;

    private final NotificationId id;
    private final NotificationType type;
    private final NotificationChannel channel;
    private final UUID clientId;
    private final UUID contractId;
    private final UUID billingId;
    private final Integer daysOffset;
    private final String recipient;
    private final Instant scheduledAt;
    private String subject;
    private Map<String, Object> payload;
    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private Instant sentAt;

    private Notification(NotificationId id, NotificationType type, NotificationChannel channel,
                         UUID clientId, UUID contractId, UUID billingId, Integer daysOffset,
                         String recipient, Instant scheduledAt) {
        this.id = Objects.requireNonNull(id, "o id é obrigatório");
        this.type = Objects.requireNonNull(type, "o tipo é obrigatório");
        this.channel = Objects.requireNonNull(channel, "o canal é obrigatório");
        this.clientId = Objects.requireNonNull(clientId, "o cliente é obrigatório");
        this.contractId = contractId;
        this.billingId = billingId;
        this.daysOffset = daysOffset;
        this.recipient = requireRecipient(channel, recipient);
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "o agendamento é obrigatório");
        this.status = NotificationStatus.PENDING;
        requireTarget();
    }

    /** Fábrica: todo aviso nasce PENDING, esperando o job de envio. */
    public static Notification schedule(NewNotification data) {
        Notification notification = new Notification(NotificationId.newId(), data.type(),
                data.channel(), data.clientId(), data.contractId(), data.billingId(),
                data.daysOffset(), data.recipient(), data.scheduledAt());
        notification.subject = data.subject();
        notification.payload = data.payload() == null ? Map.of() : Map.copyOf(data.payload());
        return notification;
    }

    /** Só a camada de persistência deve chamar. */
    public static Notification rehydrate(NotificationState state) {
        Notification notification = new Notification(state.id(), state.type(), state.channel(),
                state.clientId(), state.contractId(), state.billingId(), state.daysOffset(),
                state.recipient(), state.scheduledAt());
        notification.subject = state.subject();
        notification.payload = state.payload() == null ? Map.of() : state.payload();
        notification.status = state.status();
        notification.attempts = state.attempts();
        notification.lastError = state.lastError();
        notification.sentAt = state.sentAt();
        return notification;
    }

    // =================================================================
    // Envio
    // =================================================================

    /** Saiu. A tentativa é contada mesmo no sucesso — é dado de operação. */
    public void markSent(Instant now) {
        requirePending("SEND");
        this.attempts++;
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    /**
     * Falhou uma tentativa.
     *
     * <p>{@code permanent} separa "o SMTP está fora do ar" de "o e-mail não
     * existe". Retentar e-mail inválido quatro vezes não conserta o e-mail — e
     * ainda ocupa a fila. A regra: <b>retry só para o que pode dar certo
     * depois.</b>
     *
     * @return true quando desistiu de vez (virou FAILED)
     */
    public boolean markAttemptFailed(String error, boolean permanent) {
        requirePending("FAIL");
        this.attempts++;
        this.lastError = truncate(error);
        if (permanent || attempts >= MAX_ATTEMPTS) {
            this.status = NotificationStatus.FAILED;
            return true;
        }
        return false;
    }

    /**
     * Deixou de fazer sentido antes de sair.
     *
     * <p>O caso real: o aviso D-7 está agendado e o contrato é renovado hoje.
     * Mandar "seu contrato vence em 7 dias" depois de renovado é o tipo de
     * e-mail que gera ligação para o comercial.
     */
    public void cancel() {
        if (status != NotificationStatus.PENDING) {
            throw new BusinessRuleException("NOTIFICATION_NOT_PENDING",
                    "Só um aviso pendente pode ser cancelado");
        }
        this.status = NotificationStatus.CANCELLED;
    }

    /**
     * Reenvio manual (RF-25): devolve um aviso que falhou para a fila.
     *
     * <p>Zera o contador de propósito — quem pediu o reenvio já sabe que falhou
     * e quer as tentativas de novo. E como o índice único ignora
     * {@code FAILED}, o registro antigo não bloqueia este.
     */
    public void retry() {
        if (status != NotificationStatus.FAILED) {
            throw new BusinessRuleException("NOTIFICATION_NOT_FAILED",
                    "Só um aviso que falhou pode ser reenviado");
        }
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.lastError = null;
    }

    public boolean isPending() {
        return status == NotificationStatus.PENDING;
    }

    // =================================================================
    // Acessores
    // =================================================================

    @Override
    public NotificationId id() {
        return id;
    }

    public NotificationType type() {
        return type;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public UUID clientId() {
        return clientId;
    }

    public UUID contractId() {
        return contractId;
    }

    public UUID billingId() {
        return billingId;
    }

    public Integer daysOffset() {
        return daysOffset;
    }

    public String recipient() {
        return recipient;
    }

    public String subject() {
        return subject;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public NotificationStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public String lastError() {
        return lastError;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    @Override
    public String toString() {
        return "Notification[" + type + " D" + daysOffset + " -> " + recipient
                + ", " + status + "]";
    }

    // =================================================================
    // Invariantes
    // =================================================================

    private void requirePending(String action) {
        if (status != NotificationStatus.PENDING) {
            throw new BusinessRuleException("NOTIFICATION_NOT_PENDING",
                    "Não é possível executar '" + action + "' com o aviso em " + status);
        }
    }

    private void requireTarget() {
        if (contractId == null && billingId == null) {
            // O mesmo CHECK existe na migração. Aviso sem alvo é aviso que
            // ninguém consegue rastrear de volta ao que o originou.
            throw new BusinessRuleException(
                    "O aviso precisa apontar para um contrato ou uma cobrança");
        }
    }

    private static String requireRecipient(NotificationChannel channel, String recipient) {
        String normalized = recipient == null ? "" : recipient.strip().toLowerCase();
        if (normalized.isBlank()) {
            throw new BusinessRuleException("O destinatário é obrigatório");
        }
        if (channel == NotificationChannel.EMAIL && !EMAIL.matcher(normalized).matches()) {
            throw new BusinessRuleException("INVALID_RECIPIENT",
                    "Destinatário de e-mail inválido: " + normalized);
        }
        return normalized;
    }

    private static String truncate(String error) {
        if (error == null) {
            return "sem mensagem";
        }
        return error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
    }
}
