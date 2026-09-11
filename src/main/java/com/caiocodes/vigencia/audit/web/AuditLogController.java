package com.caiocodes.vigencia.audit.web;

import com.caiocodes.vigencia.audit.application.AuditLogDetail;
import com.caiocodes.vigencia.audit.application.AuditLogSummary;
import com.caiocodes.vigencia.audit.application.SearchAuditLogsUseCase;
import com.caiocodes.vigencia.audit.domain.AuditSearchCriteria;
import com.caiocodes.vigencia.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trilha de auditoria — leitura e mais nada.
 *
 * <p>Não existe {@code POST} aqui, e nem poderia: quem escreve é o aspecto, a
 * partir do que os casos de uso fizeram. Um endpoint de escrita permitiria
 * plantar uma linha falsa na trilha, que é o oposto do serviço que ela presta.
 */
@Tag(name = "Auditoria")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final SearchAuditLogsUseCase auditLogs;

    @Operation(summary = "Consulta a trilha de auditoria")
    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public PageResponse<AuditLogSummary> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return PageResponse.from(auditLogs.search(new AuditSearchCriteria(
                entityType, entityId, actorId, action, from, until, page, size)));
    }

    @Operation(summary = "Detalha um registro, com o antes e o depois")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('audit:read')")
    public AuditLogDetail byId(@PathVariable UUID id) {
        return auditLogs.byId(id);
    }
}
