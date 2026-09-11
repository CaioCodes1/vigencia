package com.caiocodes.vigencia.reporting;

import com.caiocodes.vigencia.billing.application.MarkOverdueBillingsUseCase;
import com.caiocodes.vigencia.reporting.application.DashboardCache;
import com.caiocodes.vigencia.support.AbstractIamIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
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

/** Fase 7: o painel responde certo — e responde a coisa certa para cada um. */
class DashboardIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String GESTOR = "gestor@empresa.com";
    private static final String VENDEDOR = "vendedor@empresa.com";
    private static final String INTEGRACAO = "erp@empresa.com";
    private static final String CNPJ_GESTOR = "11222333000181";
    private static final String CNPJ_VENDEDOR = "45997418000153";

    @Autowired private MarkOverdueBillingsUseCase markOverdue;

    // =================================================================
    // O teste que não pode faltar
    // =================================================================

    @Test
    @DisplayName("o painel do vendedor não contém dados de outra carteira")
    void painel_do_vendedor_nao_deve_vazar_outra_carteira() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");

        String clienteDoGestor = umCliente(GESTOR, CNPJ_GESTOR, "Acme do Gestor");
        String clienteDoVendedor = umCliente(VENDEDOR, CNPJ_VENDEDOR, "Beta do Vendedor");
        umContratoAtivo(clienteDoGestor, "CT-2026-0001", "1200.00");
        umContratoAtivo(clienteDoVendedor, "CT-2026-0002", "2400.00");

        // O gestor tem dashboard:read_all — vê a empresa.
        JsonNode doGestor = resumo(GESTOR);
        assertThat(doGestor.at("/contracts/active").asLong()).isEqualTo(2);

        // O vendedor tem só dashboard:read — vê a própria carteira.
        JsonNode doVendedor = resumo(VENDEDOR);
        assertThat(doVendedor.at("/contracts/active").asLong()).isEqualTo(1);

        // Os dois contratos comecaram ha 6 meses e duram 12: só 10 parcelas de
        // cada caem no ano corrente. 2400/12 x 10 = 2000 para o vendedor;
        // somado ao contrato do gestor (1200/12 x 10 = 1000), 3000 na empresa.
        assertThat(doVendedor.at("/revenue/currentYear/expected").asDouble())
                .as("2000 é a carteira dele; 3000 seria a empresa inteira")
                .isEqualTo(2000.00);
        assertThat(doGestor.at("/revenue/currentYear/expected").asDouble()).isEqualTo(3000.00);
    }

    @Test
    @DisplayName("a segunda chamada vem do cache e continua sendo a de cada um")
    void cache_nao_deve_misturar_escopos() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        umContratoAtivo(umCliente(GESTOR, CNPJ_GESTOR, "Acme"), "CT-2026-0001", "1200.00");
        umContratoAtivo(umCliente(VENDEDOR, CNPJ_VENDEDOR, "Beta"), "CT-2026-0002", "2400.00");

        // A primeira rodada enche o cache; a segunda é servida por ele. Com a
        // chave sem o escopo, é aqui que o vendedor receberia o painel do
        // gestor — e o teste de uma chamada só passaria sem ver nada.
        resumo(GESTOR);
        resumo(VENDEDOR);

        assertThat(resumo(GESTOR).at("/contracts/active").asLong()).isEqualTo(2);
        assertThat(resumo(VENDEDOR).at("/contracts/active").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("a lista de inadimplentes também respeita a carteira")
    void inadimplentes_devem_respeitar_a_carteira() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        umContratoAtivo(umCliente(GESTOR, CNPJ_GESTOR, "Acme do Gestor"),
                "CT-2026-0001", "1200.00");
        umContratoAtivo(umCliente(VENDEDOR, CNPJ_VENDEDOR, "Beta do Vendedor"),
                "CT-2026-0002", "2400.00");
        markOverdue.execute();

        mockMvc.perform(get("/api/v1/dashboard/delinquent-clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mockMvc.perform(get("/api/v1/dashboard/delinquent-clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].legalName").value("Beta do Vendedor"));
    }

    // =================================================================
    // Os números
    // =================================================================

    @Test
    @DisplayName("o resumo conta ativos, rascunhos e o que vence em 30 dias")
    void resumo_deve_contar_os_contratos() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String cliente = umCliente(GESTOR, CNPJ_GESTOR, "Acme");

        umContratoAtivo(cliente, "CT-2026-0001", "1200.00");
        // Um rascunho, que não conta como ativo.
        umContrato(cliente, "CT-2026-0002", "600.00", inicio(), fim());
        // Um ativo que vence daqui a 10 dias — entra na faixa de 30.
        String vencendo = umContrato(cliente, "CT-2026-0003", "900.00",
                LocalDate.now(clock).minusMonths(11), LocalDate.now(clock).plusDays(10));
        ativar(vencendo);

        JsonNode contratos = resumo(GESTOR).get("contracts");
        assertThat(contratos.get("active").asLong()).isEqualTo(2);
        assertThat(contratos.get("draft").asLong()).isEqualTo(1);
        assertThat(contratos.get("expiringIn30Days").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("receita realizada só conta pagamento não estornado")
    void receita_deve_ignorar_estorno() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario("financeiro@empresa.com", SENHA, "FINANCE");
        umContratoAtivo(umCliente(GESTOR, CNPJ_GESTOR, "Acme"), "CT-2026-0001", "1200.00");

        String parcela = jdbc.queryForObject(
                "SELECT id::text FROM billings ORDER BY due_date LIMIT 1", String.class);
        pagar(parcela, "100.00");

        // O pagamento entrou no ano corrente; o mês é o do vencimento da
        // parcela, que pode ser passado. Por isso a conferência é a anual.
        assertThat(resumo(GESTOR).at("/revenue/currentYear/realized").asDouble())
                .isEqualTo(100.00);

        // O estorno NÃO apaga a linha de pagamento — marca. Se a consulta
        // esquecer o "AND NOT p.refunded", a receita continua contando
        // dinheiro que voltou para o cliente.
        String pagamento = jdbc.queryForObject(
                "SELECT id::text FROM payments LIMIT 1", String.class);
        mockMvc.perform(delete("/api/v1/payments/{id}", pagamento)
                        .header(HttpHeaders.AUTHORIZATION, bearer("financeiro@empresa.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "cobrança indevida"}
                                """))
                .andExpect(status().isOk());

        // O painel é eventualmente consistente por TTL: sem esta limpeza, a
        // segunda leitura viria do cache de 5 minutos e ainda diria 100. É o
        // comportamento desejado em produção — aqui o que se confere é a
        // consulta, não o cache.
        var resumoCache = cacheManager.getCache(DashboardCache.SUMMARY);
        assertThat(resumoCache).isNotNull();
        resumoCache.clear();

        assertThat(resumo(GESTOR).at("/revenue/currentYear/realized").asDouble())
                .isZero();
    }

    @Test
    @DisplayName("a linha do tempo de vencimentos vem por faixa")
    void deve_agrupar_vencimentos_por_faixa() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String cliente = umCliente(GESTOR, CNPJ_GESTOR, "Acme");

        ativar(umContrato(cliente, "CT-2026-0001", "900.00",
                LocalDate.now(clock).minusMonths(11), LocalDate.now(clock).plusDays(5)));
        ativar(umContrato(cliente, "CT-2026-0002", "900.00",
                LocalDate.now(clock).minusMonths(11), LocalDate.now(clock).plusDays(20)));

        mockMvc.perform(get("/api/v1/dashboard/expiring-timeline?daysAhead=60")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].range").value("0-7"))
                .andExpect(jsonPath("$[0].contracts").value(1))
                .andExpect(jsonPath("$[1].range").value("16-30"))
                .andExpect(jsonPath("$[1].contracts").value(1));
    }

    // =================================================================
    // Autorização
    // =================================================================

    @Test
    @DisplayName("conta de integração não tem painel")
    void deve_negar_quem_nao_tem_dashboard_read() throws Exception {
        dadoUmUsuario(INTEGRACAO, SENHA, "INTEGRATION");

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(INTEGRACAO)))
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

    private JsonNode resumo(String usuario) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String umCliente(String comoUsuario, String documento, String nome) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(comoUsuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s",
                                 "legalName": "%s",
                                 "email": "financeiro@acme.com"}
                                """.formatted(documento, nome)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * O contrato é sempre criado pelo gestor — {@code SALES} não tem
     * {@code contract:activate}. A carteira que o painel enxerga é a do
     * <b>cliente</b>, não a de quem digitou o contrato.
     */
    private String umContrato(String cliente, String numero, String valor,
                              LocalDate de, LocalDate ate) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "number": "%s",
                                 "title": "Licença Enterprise",
                                 "startDate": "%s",
                                 "endDate": "%s",
                                 "amount": "%s",
                                 "currency": "BRL",
                                 "billingCycle": "MONTHLY",
                                 "autoRenew": false}
                                """.formatted(cliente, numero, de, ate, valor)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asText();
    }

    private void ativar(String contrato) throws Exception {
        mockMvc.perform(post("/api/v1/contracts/{id}/activate", contrato)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());
    }

    private void umContratoAtivo(String cliente, String numero, String valor) throws Exception {
        ativar(umContrato(cliente, numero, valor, inicio(), fim()));
    }

    private void pagar(String cobranca, String valor) throws Exception {
        mockMvc.perform(post("/api/v1/billings/{id}/payments", cobranca)
                        .header(HttpHeaders.AUTHORIZATION, bearer("financeiro@empresa.com"))
                        .header("Idempotency-Key", "pagamento-" + cobranca)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "%s", "method": "PIX", "paidAt": "%s"}
                                """.formatted(valor, java.time.Instant.now(clock))))
                .andExpect(status().isCreated());
    }
}
