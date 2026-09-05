package com.caiocodes.crbap.audit.application;

import com.caiocodes.crbap.audit.domain.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A linha da listagem de auditoria.
 *
 * <p>Sem {@code before} e {@code after} de propósito: são dois JSONs por linha,
 * e uma página de cem viraria megabytes que ninguém lê na listagem. Quem quer o
 * conteúdo abre o detalhe. O {@code changedFields} fica, porque é ele que faz a
 * listagem valer alguma coisa — "mudou o valor" é o que se procura.
 */
public record AuditLogSummary(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        String actorEmail,
        String ipAddress,
        List<String> changedFields,
        Instant createdAt) {

    public static AuditLogSummary from(AuditEntry entry) {
        return new AuditLogSummary(
                entry.id(), entry.entityType(), entry.entityId(), entry.action(),
                entry.actorId(), entry.actorEmail(), entry.ipAddress(),
                entry.changedFields(), entry.createdAt());
    }
}
