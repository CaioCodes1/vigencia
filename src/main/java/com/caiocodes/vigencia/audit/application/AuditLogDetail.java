package com.caiocodes.vigencia.audit.application;

import com.caiocodes.vigencia.audit.domain.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A linha inteira, com o antes e o depois. */
public record AuditLogDetail(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        Actor actor,
        String ipAddress,
        String userAgent,
        String traceId,
        List<String> changedFields,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant createdAt) {

    public static AuditLogDetail from(AuditEntry entry, String actorName) {
        return new AuditLogDetail(
                entry.id(), entry.entityType(), entry.entityId(), entry.action(),
                actorDe(entry, actorName),
                entry.ipAddress(), entry.userAgent(), entry.traceId(),
                entry.changedFields(), entry.before(), entry.after(), entry.createdAt());
    }

    private static Actor actorDe(AuditEntry entry, String actorName) {
        if (entry.actorId() == null && entry.actorEmail() == null) {
            // Ação de job: sem autor, e é essa a informação.
            return null;
        }
        return new Actor(entry.actorId(), entry.actorEmail(), actorName);
    }

    /**
     * O nome vem do cadastro na hora da leitura; o e-mail veio congelado na
     * linha. Se o usuário foi excluído, o nome vem nulo e o e-mail continua
     * respondendo quem foi — que é exatamente para isso que ele é
     * desnormalizado.
     */
    public record Actor(UUID id, String email, String name) {
    }
}
