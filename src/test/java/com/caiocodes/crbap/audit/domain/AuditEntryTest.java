package com.caiocodes.crbap.audit.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O que a trilha consegue dizer sozinha, sem banco e sem Spring. */
class AuditEntryTest {

    @Test
    @DisplayName("changedFields lista só o que mudou de valor")
    void deve_listar_apenas_o_que_mudou() {
        Map<String, Object> antes = Map.of("title", "Licença", "amount", "24000.00",
                "autoRenew", false);
        Map<String, Object> depois = Map.of("title", "Licença", "amount", "26400.00",
                "autoRenew", true);

        assertThat(AuditEntry.changedFields(antes, depois))
                .containsExactlyInAnyOrder("amount", "autoRenew");
    }

    @Test
    @DisplayName("campo que sumiu ou apareceu conta como mudança")
    void deve_pegar_campo_que_entrou_ou_saiu() {
        Map<String, Object> antes = Map.of("title", "Licença");
        Map<String, Object> depois = Map.of("title", "Licença", "cancellationReason", "atraso");

        assertThat(AuditEntry.changedFields(antes, depois)).containsExactly("cancellationReason");
    }

    @Test
    @DisplayName("sem o 'antes', a lista é vazia — e não a lista de tudo")
    void criacao_nao_deve_listar_campos() {
        // Numa criação, "mudou" não quer dizer nada: todo campo é novo. Uma
        // lista com os trinta campos do contrato só atrapalharia quem lê.
        Map<String, Object> depois = Map.of("title", "Licença", "amount", "24000.00");

        assertThat(AuditEntry.changedFields(null, depois)).isEmpty();
        assertThat(AuditEntry.changedFields(Map.of(), depois)).isEmpty();
    }

    @Test
    @DisplayName("nulo dos dois lados não vira mudança")
    void nulo_igual_nao_deve_mudar() {
        Map<String, Object> antes = new HashMap<>();
        antes.put("description", null);
        antes.put("title", "Licença");
        Map<String, Object> depois = new HashMap<>();
        depois.put("description", null);
        depois.put("title", "Outro");

        assertThat(AuditEntry.changedFields(antes, depois)).containsExactly("title");
    }

    @Test
    @DisplayName("ação de job nasce sem autor e sem origem, e isso é a informação")
    void deve_aceitar_acao_sem_autor() {
        AuditEntry entry = AuditEntry.of("Contract", UUID.randomUUID(), "EXPIRE",
                null, null, null, Map.of("status", "EXPIRED"), Instant.now());

        assertThat(entry.actorId()).isNull();
        assertThat(entry.actorEmail()).isNull();
        assertThat(entry.ipAddress()).isNull();
        assertThat(entry.after()).containsEntry("status", "EXPIRED");
    }
}
