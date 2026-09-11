package com.caiocodes.vigencia.audit.application;

import com.caiocodes.vigencia.audit.domain.AuditEntry;
import com.caiocodes.vigencia.audit.domain.AuditSearchCriteria;
import com.caiocodes.vigencia.audit.domain.AuditTrail;
import com.caiocodes.vigencia.shared.application.UserDirectory;
import com.caiocodes.vigencia.shared.domain.PageResult;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As leituras da trilha.
 *
 * <p><b>Não tem escopo por carteira</b>, ao contrário de contratos e cobranças.
 * Auditoria filtrada pelo próprio interessado não é auditoria: quem tem
 * {@code audit:read} — só {@code ADMIN} e {@code AUDITOR} — vê tudo, e quem não
 * tem não vê nada. A defesa aqui é a permissão, não o recorte.
 */
@Service
@RequiredArgsConstructor
public class SearchAuditLogsUseCase {

    private final AuditTrail trail;
    private final UserDirectory users;

    @Transactional(readOnly = true)
    public PageResult<AuditLogSummary> search(AuditSearchCriteria criteria) {
        return trail.search(criteria).map(AuditLogSummary::from);
    }

    /**
     * O nome do autor é resolvido só aqui, no detalhe.
     *
     * <p>Na listagem seria uma consulta por linha — o N+1 clássico — para
     * enfeitar uma coluna que o e-mail já responde.
     */
    @Transactional(readOnly = true)
    public AuditLogDetail byId(UUID id) {
        AuditEntry entry = trail.findById(id)
                .orElseThrow(() -> new NotFoundException("Registro de auditoria", id));
        return AuditLogDetail.from(entry, nomeDoAutor(entry.actorId()));
    }

    private String nomeDoAutor(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return users.findRef(actorId)
                .map(UserDirectory.UserRef::fullName)
                .orElse(null);
    }
}
