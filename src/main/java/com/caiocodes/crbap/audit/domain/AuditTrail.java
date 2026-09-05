package com.caiocodes.crbap.audit.domain;

import com.caiocodes.crbap.shared.domain.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * A trilha de auditoria: só escreve e lê.
 *
 * <p>Não existe {@code update} nem {@code delete} nesta interface, e a ausência
 * é a documentação: o banco recusa os dois (gatilho da V6), então oferecê-los
 * aqui seria prometer o que a camada de baixo nega.
 */
public interface AuditTrail {

    void record(AuditEntry entry);

    Optional<AuditEntry> findById(UUID id);

    PageResult<AuditEntry> search(AuditSearchCriteria criteria);
}
