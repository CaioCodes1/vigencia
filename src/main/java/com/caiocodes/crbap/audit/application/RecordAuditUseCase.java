package com.caiocodes.crbap.audit.application;

import com.caiocodes.crbap.audit.domain.AuditEntry;
import com.caiocodes.crbap.audit.domain.AuditTrail;
import com.caiocodes.crbap.shared.application.CurrentUser;
import com.caiocodes.crbap.shared.application.UserDirectory;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grava uma linha da trilha. Chamado pelo aspecto, nunca pelos controllers. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAuditUseCase {

    private final AuditTrail trail;
    private final CurrentUser currentUser;
    private final UserDirectory users;
    private final Clock clock;

    /**
     * Transação própria, separada da do negócio.
     *
     * <p>{@code REQUIRES_NEW} porque as duas gravações têm donos diferentes: a
     * do negócio pode ter commitado há um instante (o aspecto roda por fora da
     * transação, de propósito) e, se um job auditado já estiver dentro de uma
     * transação, a auditoria não pode ser arrastada por um rollback dele —
     * "tentou e falhou" também é fato auditável.
     *
     * <p>Custa uma segunda conexão do pool por ação auditada. É o preço de a
     * trilha não depender do destino da transação que ela observa.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(AuditRequest request) {
        if (request.entityId() == null) {
            // Sem id não há o que auditar, e o INSERT quebraria no NOT NULL.
            // Erro, não warning: é bug na expressão SpEL da anotação, e ficar
            // invisível significa uma ação que ninguém vai conseguir rastrear.
            log.error("audit.sem_id entidade={} acao={}", request.entity(), request.action());
            return;
        }

        AuditEntry.Actor actor = actorAtual();
        trail.record(AuditEntry.of(
                request.entity(), request.entityId(), request.action(),
                actor,
                new AuditEntry.RequestOrigin(
                        request.ipAddress(), request.userAgent(), request.traceId()),
                request.before(), request.after(),
                clock.instant()));
    }

    /**
     * O autor sai do contexto de segurança, nunca do corpo da requisição.
     *
     * <p>É a mesma regra do escopo por carteira: aceitar "quem sou eu" vindo do
     * cliente transforma a trilha em ficção — o invasor assina como outro.
     *
     * <p>Nulo em job agendado, que roda sem usuário. A linha continua útil: diz
     * o que mudou e quando, e a ausência de autor <i>é</i> a informação de que
     * foi o sistema.
     */
    private AuditEntry.Actor actorAtual() {
        UUID id = currentUser.id();
        if (id == null) {
            return null;
        }
        String email = users.findRef(id)
                .map(UserDirectory.UserRef::email)
                .orElse(null);
        return new AuditEntry.Actor(id, email);
    }

    /** O que o aspecto conseguiu apurar sozinho, antes de saber quem é o autor. */
    public record AuditRequest(
            String entity,
            UUID entityId,
            String action,
            String ipAddress,
            String userAgent,
            String traceId,
            Map<String, Object> before,
            Map<String, Object> after) {
    }
}
