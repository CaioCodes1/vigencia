package com.caiocodes.vigencia.audit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Os filtros de {@code GET /audit-logs}.
 *
 * <p>Todos opcionais menos a paginação. Auditoria sem filtro é a tabela
 * inteira — e é por isso que a página tem teto: uma consulta sem limite numa
 * tabela append-only de milhões de linhas derruba o banco de quem investiga.
 */
public record AuditSearchCriteria(
        String entityType,
        UUID entityId,
        UUID actorId,
        String action,
        Instant from,
        Instant until,
        int page,
        int size) {

    public static final int MAX_SIZE = 100;

    public AuditSearchCriteria {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), MAX_SIZE);
    }
}
