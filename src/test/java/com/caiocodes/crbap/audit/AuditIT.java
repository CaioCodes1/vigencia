package com.caiocodes.crbap.audit;

import com.caiocodes.crbap.support.AbstractIamIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 7: a trilha grava o que aconteceu, e ninguém consegue alterá-la depois. */
class AuditIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String GESTOR = "gestor@empresa.com";
    private static final String AUDITOR = "auditor@empresa.com";
    private static final String CNPJ = "11222333000181";

    // =================================================================
    // O aspecto grava
    // =================================================================

    @Test
    @DisplayName("renovar um contrato grava RENEW com autor, IP e traceId")
    void deve_auditar_a_renovacao() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contrato = umContratoAtivo();

        renovar(contrato).andExpect(status().isCreated());

        Map<String, Object> linha = umaLinha("Contract", "RENEW");
        assertThat(linha.get("actor_email")).isEqualTo(GESTOR);
        assertThat(linha.get("ip_address")).asString().isNotBlank();
        assertThat(linha.get("trace_id")).asString().isNotBlank();
        assertThat(linha.get("after_data")).asString()
                .as("o after_data documenta o sucessor que nasceu")
                .contains("-R1");
    }

    @Test
    @DisplayName("editar em rascunho grava o antes, o depois e os campos que mudaram")
    void deve_gravar_o_diff_da_edicao() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contrato = umContratoEmRascunho();

        mockMvc.perform(patch("/api/v1/contracts/{id}", contrato)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Licença Enterprise Plus",
                                 "startDate": "%s",
                                 "endDate": "%s",
                                 "amount": "26400.00",
                                 "currency": "BRL",
                                 "billingCycle": "MONTHLY",
                                 "autoRenew": true}
                                """.formatted(inicio(), fim())))
                .andExpect(status().isOk());

        Map<String, Object> linha = umaLinha("Contract", "UPDATE");
        assertThat(linha.get("before_data")).asString().contains("Licença Enterprise");
        assertThat(linha.get("after_data")).asString().contains("Licença Enterprise Plus");

        List<String> mudaram = camposAlterados(linha);
        assertThat(mudaram)
                .as("é o serviço que a trilha presta: 'olhe aqui', e não dois JSONs de trinta campos")
                .contains("title", "autoRenew")
                .doesNotContain("number", "clientId");
    }

    @Test
    @DisplayName("ação que falhou NÃO vira linha de auditoria")
    void tentativa_frustrada_nao_deve_ser_auditada() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String rascunho = umContratoEmRascunho();

        // Renovar um contrato em DRAFT é recusado pelo agregado. O aspecto roda
        // POR FORA da transação: sem commit, não há o que auditar. Se esta
        // contagem virar 1, a trilha estará dizendo que o contrato foi renovado.
        renovar(rascunho).andExpect(status().is4xxClientError());

        assertThat(quantas("Contract", "RENEW")).isZero();
    }

    @Test
    @DisplayName("o documento do cliente não entra na trilha")
    void deve_redigir_dado_sensivel() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umCliente();

        Map<String, Object> linha = umaLinha("Client", "CREATE");
        assertThat(linha.get("after_data")).asString()
                .as("auditoria é lida por muito mais gente que a tabela de clientes")
                .doesNotContain(CNPJ)
                .contains("\"document\": \"***\"");
    }

    // =================================================================
    // A trilha é imutável — no banco, não no código
    // =================================================================

    @Test
    @DisplayName("UPDATE e DELETE em audit_logs são recusados pelo banco")
    void trilha_deve_ser_imutavel() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umCliente();

        // A causa raiz é que carrega a mensagem do gatilho: o Spring traduz o
        // SQLSTATE 42501 (insufficient_privilege) para a família de "grammar",
        // e o texto do RAISE fica uma camada abaixo.
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'MENTIRA'"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs"))
                .as("nem um bug nosso apaga a trilha")
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("append-only");
    }

    // =================================================================
    // A consulta
    // =================================================================

    @Test
    @DisplayName("GET /audit-logs filtra por entidade e devolve o mais recente primeiro")
    void deve_filtrar_a_consulta() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(AUDITOR, SENHA, "AUDITOR");
        umContratoAtivo();

        mockMvc.perform(get("/api/v1/audit-logs?entityType=Contract&action=ACTIVATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("ACTIVATE"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(GESTOR));
    }

    @Test
    @DisplayName("o detalhe traz o nome do autor, resolvido no cadastro")
    void detalhe_deve_trazer_o_nome_do_autor() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(AUDITOR, SENHA, "AUDITOR");
        umCliente();

        String id = jdbc.queryForObject(
                "SELECT id::text FROM audit_logs WHERE action = 'CREATE' LIMIT 1", String.class);

        mockMvc.perform(get("/api/v1/audit-logs/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actor.email").value(GESTOR))
                .andExpect(jsonPath("$.actor.name").value("Usuário de Teste"))
                .andExpect(jsonPath("$.after.document").value("***"));
    }

    @Test
    @DisplayName("quem não tem audit:read não lê a trilha — nem o gestor comercial")
    void deve_exigir_a_permissao() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // Apoio
    // =================================================================

    private String bearer(String email) throws Exception {
        return "Bearer " + tokenDe(email, SENHA);
    }

    private LocalDate inicio() {
        return LocalDate.now(clock).minusMonths(6).withDayOfMonth(1);
    }

    private LocalDate fim() {
        return inicio().plusYears(1).minusDays(1);
    }

    private String umCliente() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s",
                                 "legalName": "Acme Comércio Ltda",
                                 "email": "financeiro@acme.com"}
                                """.formatted(CNPJ)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String umContratoEmRascunho() throws Exception {
        String cliente = umCliente();
        MvcResult criado = mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "number": "CT-2026-0042",
                                 "title": "Licença Enterprise",
                                 "startDate": "%s",
                                 "endDate": "%s",
                                 "amount": "24000.00",
                                 "currency": "BRL",
                                 "billingCycle": "MONTHLY",
                                 "autoRenew": false}
                                """.formatted(cliente, inicio(), fim())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asText();
    }

    private String umContratoAtivo() throws Exception {
        String contrato = umContratoEmRascunho();
        mockMvc.perform(post("/api/v1/contracts/{id}/activate", contrato)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());
        return contrato;
    }

    private org.springframework.test.web.servlet.ResultActions renovar(String contrato)
            throws Exception {
        return mockMvc.perform(post("/api/v1/contracts/{id}/renew", contrato)
                .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                .header("Idempotency-Key", "renovacao-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"endDate": "%s", "amount": "26400.00", "currency": "BRL"}
                        """.formatted(fim().plusYears(1))));
    }

    private Map<String, Object> umaLinha(String entidade, String acao) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT actor_email, host(ip_address) AS ip_address, trace_id,
                       before_data::text AS before_data, after_data::text AS after_data,
                       changed_fields
                  FROM audit_logs
                 WHERE entity_type = ? AND action = ?
                 ORDER BY created_at DESC
                """, entidade, acao);
        assertThat(linhas).as("nenhuma linha de %s/%s na trilha", entidade, acao).isNotEmpty();
        return linhas.get(0);
    }

    private long quantas(String entidade, String acao) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type = ? AND action = ?",
                Long.class, entidade, acao);
        return total == null ? 0 : total;
    }

    private List<String> camposAlterados(Map<String, Object> linha) {
        try {
            java.sql.Array array = (java.sql.Array) linha.get("changed_fields");
            return array == null ? List.of() : List.of((String[]) array.getArray());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
