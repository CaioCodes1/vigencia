package com.caiocodes.vigencia.audit.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uma linha da trilha: o que aconteceu, com o quê, por quem e de onde.
 *
 * <p>É um {@code record} e não um agregado de propósito. Auditoria não tem
 * invariante para proteger nem máquina de estados para percorrer — nasce
 * pronta e nunca muda. Um agregado aqui só acrescentaria cerimônia.
 *
 * <p>Não confundir com o evento de domínio: o evento diz <i>o que o negócio
 * fez</i> e é consumido por outros módulos; a auditoria diz <i>quem apertou o
 * botão, de qual IP</i> e é lida por gente. O mesmo `renew` gera os dois, e
 * apagar um não substitui o outro.
 */
public record AuditEntry(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        String actorEmail,
        String ipAddress,
        String userAgent,
        String traceId,
        Map<String, Object> before,
        Map<String, Object> after,
        List<String> changedFields,
        Instant createdAt) {

    public AuditEntry {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Monta a linha já com a lista de campos alterados calculada.
     *
     * <p>O {@code changedFields} existe para a pergunta que a tela de auditoria
     * realmente recebe: "o que mudou nesta edição?". Sem ele, quem investiga
     * compara dois JSONs de trinta campos no olho para achar o único diferente.
     */
    public static AuditEntry of(String entityType, UUID entityId, String action,
                                Actor actor, RequestOrigin origin,
                                Map<String, Object> before, Map<String, Object> after,
                                Instant createdAt) {
        return new AuditEntry(
                UUID.randomUUID(), entityType, entityId, action,
                actor == null ? null : actor.id(),
                actor == null ? null : actor.email(),
                origin == null ? null : origin.ipAddress(),
                origin == null ? null : origin.userAgent(),
                origin == null ? null : origin.traceId(),
                before, after, changedFields(before, after), createdAt);
    }

    /**
     * Os campos cujo valor mudou entre o antes e o depois.
     *
     * <p>Compara só o primeiro nível. Campo aninhado que muda aparece como o
     * nome do pai — e é o suficiente para "vá olhar aqui", que é o serviço que
     * esta lista presta. Diff recursivo de JSON é bonito e ninguém usa.
     *
     * <p>Sem o "antes" (uma criação, por exemplo) a lista é vazia, não a lista
     * de tudo o que foi preenchido: em um CREATE, "mudou" não quer dizer nada.
     */
    static List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        if (before == null || before.isEmpty() || after == null || after.isEmpty()) {
            return List.of();
        }
        Set<String> chaves = new LinkedHashSet<>(before.keySet());
        chaves.addAll(after.keySet());

        List<String> mudaram = new ArrayList<>();
        for (String chave : chaves) {
            if (!Objects.equals(before.get(chave), after.get(chave))) {
                mudaram.add(chave);
            }
        }
        return List.copyOf(mudaram);
    }

    /** Quem agiu. Nulo quando a ação partiu de um job, não de uma pessoa. */
    public record Actor(UUID id, String email) { }

    /** De onde veio a requisição. Nulo fora de um contexto HTTP. */
    public record RequestOrigin(String ipAddress, String userAgent, String traceId) { }
}
