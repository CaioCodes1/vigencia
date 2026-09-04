package com.caiocodes.crbap.contract;

import com.caiocodes.crbap.contract.application.ExpireContractsUseCase;
import com.caiocodes.crbap.support.AbstractIamIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 4 de ponta a ponta: ciclo de vida, renovação encadeada e vencimento. */
class ContractIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String GESTOR = "gestor@empresa.com";
    private static final String VENDEDOR = "vendedor@empresa.com";
    private static final String CNPJ = "11222333000181";

    @Autowired private ExpireContractsUseCase expireContracts;

    @BeforeEach
    void limparContratos() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM contracts");
        jdbc.update("DELETE FROM client_contacts");
        jdbc.update("DELETE FROM clients");
    }

    // =================================================================
    // Criação e ativação
    // =================================================================

    @Test
    @DisplayName("cria em DRAFT: não existe como pedir outro estado pela API")
    void deve_criar_contrato_em_rascunho() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);

        mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "number": "CT-2026-0042",
                                 "title": "Licença Enterprise",
                                 "startDate": "2026-10-01",
                                 "endDate": "2027-09-30",
                                 "amount": "24000.00",
                                 "currency": "BRL",
                                 "billingCycle": "MONTHLY",
                                 "billingDay": 10,
                                 "autoRenew": true}
                                """.formatted(clienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").value("CT-2026-0042"))
                .andExpect(jsonPath("$.clientName").value("Acme Comércio Ltda"));
    }

    @Test
    @DisplayName("número omitido é gerado pela sequência do banco, no formato CT-<ano>-<seq>")
    void deve_gerar_o_numero_quando_omitido() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);

        String numero = criarContrato(GESTOR, clienteId, null, "2027-09-30")
                .get("number").asText();

        assertThat(numero).matches("CT-\\d{4}-\\d{4,}");
    }

    @Test
    @DisplayName("número repetido é recusado com 409")
    void deve_recusar_numero_duplicado() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);
        criarContrato(GESTOR, clienteId, "CT-2026-0001", "2027-09-30");

        mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoContrato(clienteId, "CT-2026-0001", "2027-09-30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CONTRACT_NUMBER"));
    }

    @Test
    @DisplayName("cliente inativo não recebe contrato novo")
    void deve_recusar_contrato_para_cliente_inativo() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);
        mockMvc.perform(delete("/api/v1/clients/" + clienteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoContrato(clienteId, "CT-2026-0002", "2027-09-30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("ativa o contrato e grava o evento contract.activated na outbox")
    void deve_ativar_e_registrar_evento() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        assertThat(eventosDoAgregado(contratoId))
                .containsExactly("contract.created", "contract.activated");
    }

    @Test
    @DisplayName("ativar duas vezes devolve 409 — a máquina de estados não permite")
    void nao_deve_ativar_duas_vezes() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    // =================================================================
    // Renovação — o coração da fase
    // =================================================================

    @Test
    @DisplayName("renova criando o sucessor, encerra o anterior e encadeia sem buraco")
    void deve_renovar_encadeando() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        JsonNode sucessor = renovar(contratoId, "chave-A", "2028-09-30", "26400.00");

        assertThat(sucessor.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(sucessor.get("number").asText()).endsWith("-R1");
        assertThat(sucessor.get("previousContractId").asText()).isEqualTo(contratoId);
        // Sem buraco: o sucessor começa no dia seguinte ao fim do anterior.
        assertThat(sucessor.get("startDate").asText()).isEqualTo("2027-10-01");

        mockMvc.perform(get("/api/v1/contracts/" + contratoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(jsonPath("$.status").value("RENEWED"));
    }

    @Test
    @DisplayName("repetir a mesma Idempotency-Key devolve 200 com o MESMO contrato")
    void deve_ser_idempotente_na_renovacao() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        JsonNode primeira = renovar(contratoId, "chave-B", "2028-09-30", "26400.00");

        MvcResult segunda = mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/renew")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .header("Idempotency-Key", "chave-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endDate": "2028-09-30", "amount": "26400.00",
                                 "currency": "BRL"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode corpo = objectMapper.readTree(segunda.getResponse().getContentAsString());
        assertThat(corpo.get("id").asText()).isEqualTo(primeira.get("id").asText());
        assertThat(quantosContratos()).isEqualTo(2);
    }

    @Test
    @DisplayName("renovar sem Idempotency-Key é 400, não 500")
    void deve_exigir_chave_de_idempotencia() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/renew")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endDate": "2028-09-30"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    @DisplayName("duas renovações simultâneas geram apenas UM sucessor — é o FOR UPDATE")
    void duas_renovacoes_simultaneas_devem_gerar_apenas_um_sucessor() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();
        String token = bearer(GESTOR);

        // Chaves DIFERENTES de propósito: com a mesma chave a idempotência
        // resolveria sozinha e o teste não provaria nada sobre o lock.
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> renovacao = () -> {
                largada.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/renew")
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .header("Idempotency-Key", "concorrente-"
                                        + Thread.currentThread().threadId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"endDate": "2028-09-30", "amount": "26400.00",
                                         "currency": "BRL"}
                                        """))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> primeira = pool.submit(renovacao);
            Future<Integer> segunda = pool.submit(renovacao);
            largada.countDown();

            List<Integer> status = List.of(primeira.get(30, TimeUnit.SECONDS),
                    segunda.get(30, TimeUnit.SECONDS));

            assertThat(status).as("uma cria (201) e a outra encontra RENEWED (409)")
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }

        Long sucessores = jdbc.queryForObject(
                "SELECT count(*) FROM contracts WHERE previous_contract_id = ?::uuid",
                Long.class, contratoId);
        assertThat(sucessores).isEqualTo(1L);
    }

    @Test
    @DisplayName("a cadeia devolve os três elos em ordem, com o total acumulado")
    void deve_devolver_a_cadeia_completa() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String primeiro = umContratoAtivo();
        JsonNode segundo = renovar(primeiro, "cadeia-1", "2028-09-30", "26400.00");
        renovar(segundo.get("id").asText(), "cadeia-2", "2029-09-30", "29000.00");

        // Consultada a partir do elo do MEIO, que é como o usuário costuma chegar.
        MvcResult resultado = mockMvc.perform(
                        get("/api/v1/contracts/" + segundo.get("id").asText() + "/chain")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chain.length()").value(3))
                .andExpect(jsonPath("$.renewalCount").value(2))
                .andExpect(jsonPath("$.chain[0].status").value("RENEWED"))
                .andExpect(jsonPath("$.chain[2].status").value("ACTIVE"))
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(corpo.get("totalLifetimeValue").decimalValue())
                .isEqualByComparingTo(new BigDecimal("79400.00"));
    }

    // =================================================================
    // Cancelamento e suspensão
    // =================================================================

    @Test
    @DisplayName("cancelar sem motivo suficiente é 400 — o motivo vira histórico")
    void deve_exigir_motivo_no_cancelamento() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "ok"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("suspende e retoma, e o banco recusa status CANCELLED sem motivo")
    void deve_suspender_e_retomar() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivo();

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Inadimplência acima de 60 dias"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // =================================================================
    // RF-03 de verdade: agora existe quem responda
    // =================================================================

    @Test
    @DisplayName("cliente com contrato ativo não pode ser desativado — a porta agora responde")
    void nao_deve_desativar_cliente_com_contrato_ativo() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);
        String contratoId = criarContrato(GESTOR, clienteId, "CT-2026-0100", "2027-09-30")
                .get("id").asText();
        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/clients/" + clienteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_HAS_ACTIVE_CONTRACTS"));
    }

    // =================================================================
    // Escopo de carteira e permissões
    // =================================================================

    @Test
    @DisplayName("vendedor não enxerga contrato de outra carteira — 404, nunca 403")
    void vendedor_nao_deve_ver_contrato_alheio() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        String clienteDoGestor = umCliente(GESTOR, CNPJ);
        String contratoId = criarContrato(GESTOR, clienteDoGestor, "CT-2026-0200", "2027-09-30")
                .get("id").asText();

        mockMvc.perform(get("/api/v1/contracts/" + contratoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("vendedor não tem contract:renew — 403")
    void vendedor_nao_deve_renovar() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        String contratoId = umContratoAtivo();

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/renew")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .header("Idempotency-Key", "sem-permissao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endDate": "2028-09-30"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem token não passa nem na porta")
    void deve_exigir_autenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/contracts"))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // Vencimentos
    // =================================================================

    @Test
    @DisplayName("/expiring devolve o que vence na janela, agrupado por faixa")
    void deve_listar_vencendo_por_faixa() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);
        LocalDate hoje = LocalDate.now(clock);

        ativar(criarContrato(GESTOR, clienteId, "CT-EXP-05",
                hoje.plusDays(5).toString()).get("id").asText());
        ativar(criarContrato(GESTOR, clienteId, "CT-EXP-20",
                hoje.plusDays(20).toString()).get("id").asText());
        ativar(criarContrato(GESTOR, clienteId, "CT-EXP-200",
                hoje.plusDays(200).toString()).get("id").asText());

        mockMvc.perform(get("/api/v1/contracts/expiring?daysAhead=30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.buckets['0-7']").value(1))
                .andExpect(jsonPath("$.summary.buckets['16-30']").value(1))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("a varredura diária expira os ativos vencidos e ignora os demais")
    void deve_expirar_contratos_vencidos() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);
        String vencido = criarContrato(GESTOR, clienteId, "CT-VENC", "2027-09-30")
                .get("id").asText();
        ativar(vencido);
        String vigente = criarContrato(GESTOR, clienteId, "CT-VIG",
                LocalDate.now(clock).plusYears(2).toString()).get("id").asText();
        ativar(vigente);

        // Empurra o fim para o passado direto no banco: é o equivalente a
        // "amanheceu" sem precisar de um relógio mutável no contexto inteiro.
        jdbc.update("UPDATE contracts SET start_date = ?, end_date = ? WHERE id = ?::uuid",
                LocalDate.now(clock).minusYears(1), LocalDate.now(clock).minusDays(1), vencido);

        int expirados = expireContracts.execute();

        assertThat(expirados).isEqualTo(1);
        assertThat(statusDe(vencido)).isEqualTo("EXPIRED");
        assertThat(statusDe(vigente)).isEqualTo("ACTIVE");
        assertThat(eventosDoAgregado(vencido)).contains("contract.expired");
    }

    // =================================================================
    // Auxiliares
    // =================================================================

    private String bearer(String email) throws Exception {
        return "Bearer " + tokenDe(email, SENHA);
    }

    private String umCliente(String comoUsuario, String documento) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(comoUsuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s",
                                 "legalName": "Acme Comércio Ltda",
                                 "email": "financeiro@acme.com"}
                                """.formatted(documento)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Cliente + contrato + ativação, que é o ponto de partida da maioria dos testes. */
    private String umContratoAtivo() throws Exception {
        String clienteId = umCliente(GESTOR, CNPJ);
        String contratoId = criarContrato(GESTOR, clienteId, "CT-2026-0042", "2027-09-30")
                .get("id").asText();
        ativar(contratoId);
        return contratoId;
    }

    private void ativar(String contratoId) throws Exception {
        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());
    }

    private JsonNode criarContrato(String comoUsuario, String clienteId, String numero,
                                   String fim) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(comoUsuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoContrato(clienteId, numero, fim)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode renovar(String contratoId, String chave, String fim, String valor)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/renew")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endDate": "%s", "amount": "%s", "currency": "BRL"}
                                """.formatted(fim, valor)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String corpoContrato(String clienteId, String numero, String fim) {
        String campoNumero = numero == null ? "" : "\"number\": \"%s\",".formatted(numero);
        return """
                {"clientId": "%s", %s
                 "title": "Licença Enterprise",
                 "startDate": "2026-01-01",
                 "endDate": "%s",
                 "amount": "24000.00",
                 "currency": "BRL",
                 "billingCycle": "MONTHLY",
                 "billingDay": 10,
                 "autoRenew": true}
                """.formatted(clienteId, campoNumero, fim);
    }

    private String statusDe(String contratoId) {
        return jdbc.queryForObject("SELECT status FROM contracts WHERE id = ?::uuid",
                String.class, contratoId);
    }

    private Long quantosContratos() {
        return jdbc.queryForObject("SELECT count(*) FROM contracts", Long.class);
    }

    private List<String> eventosDoAgregado(String contratoId) {
        return jdbc.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ?::uuid "
                        + "ORDER BY occurred_at", String.class, contratoId);
    }
}
