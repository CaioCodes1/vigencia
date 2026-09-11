package com.caiocodes.vigencia.billing;

import com.caiocodes.vigencia.billing.application.MarkOverdueBillingsUseCase;
import com.caiocodes.vigencia.support.AbstractIamIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

/** Fase 5 de ponta a ponta: geração das parcelas, recebimento e estorno. */
class BillingIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String GESTOR = "gestor@empresa.com";
    private static final String FINANCEIRO = "financeiro@empresa.com";
    private static final String VENDEDOR = "vendedor@empresa.com";
    private static final String CNPJ = "11222333000181";

    @Autowired private MarkOverdueBillingsUseCase markOverdue;

    // =================================================================
    // Geração na ativação — ADR-006
    // =================================================================

    @Test
    @DisplayName("ativar o contrato gera as 12 parcelas, e a soma bate com o valor contratado")
    void ativar_deve_gerar_as_parcelas() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoDe("1200.00");

        BigDecimal soma = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM billings", BigDecimal.class);
        Long quantas = jdbc.queryForObject("SELECT count(*) FROM billings", Long.class);

        assertThat(quantas).isEqualTo(12L);
        assertThat(soma)
                .as("nenhum centavo pode nascer nem sumir entre o contrato e as cobranças")
                .isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("um valor que não divide redondo ainda soma exatamente o do contrato")
    void a_soma_deve_bater_mesmo_com_sobra_de_centavos() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoDe("1000.00");

        BigDecimal soma = jdbc.queryForObject(
                "SELECT SUM(amount) FROM billings", BigDecimal.class);
        BigDecimal ultima = jdbc.queryForObject(
                "SELECT amount FROM billings ORDER BY installment DESC LIMIT 1",
                BigDecimal.class);

        assertThat(soma).isEqualByComparingTo("1000.00");
        // 1000/12 = 83,333… — a sobra cai na última parcela.
        assertThat(ultima).isGreaterThan(new BigDecimal("83.33"));
    }

    @Test
    @DisplayName("as parcelas nascem PENDING, numeradas e com a referência do contrato")
    void as_parcelas_devem_nascer_identificadas() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoDe("1200.00");

        mockMvc.perform(get("/api/v1/billings?page=0&size=50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(12))
                .andExpect(jsonPath("$.content[0].installment").value(1))
                .andExpect(jsonPath("$.content[0].totalInstallments").value(12))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].reference").value(
                        org.hamcrest.Matchers.endsWith("1/12")));
    }

    @Test
    @DisplayName("o evento billing.issued vai para a outbox, uma vez por parcela")
    void deve_gravar_os_eventos_na_outbox() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoDe("1200.00");

        Long emitidas = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'billing.issued'",
                Long.class);

        assertThat(emitidas).isEqualTo(12L);
    }

    // =================================================================
    // Pagamento
    // =================================================================

    @Test
    @DisplayName("pagamento parcial deixa PARTIALLY_PAID; o resto quita")
    void deve_receber_parcial_e_depois_quitar() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        pagar(cobranca, "40.00", "parcial-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.remaining").value(60.00));

        pagar(cobranca, "60.00", "parcial-2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.remaining").value(0.00));
    }

    @Test
    @DisplayName("repetir a Idempotency-Key devolve 200 e NÃO cobra duas vezes")
    void deve_ser_idempotente_no_pagamento() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        pagar(cobranca, "100.00", "webhook-pix-1").andExpect(status().isCreated());

        MvcResult repetida = pagar(cobranca, "100.00", "webhook-pix-1")
                .andExpect(status().isOk())
                .andReturn();

        JsonNode corpo = objectMapper.readTree(repetida.getResponse().getContentAsString());
        assertThat(corpo.get("status").asText()).isEqualTo("PAID");
        assertThat(corpo.get("payments")).hasSize(1);

        Long pagamentos = jdbc.queryForObject("SELECT count(*) FROM payments", Long.class);
        assertThat(pagamentos).as("o mesmo PIX não pode virar dois pagamentos").isEqualTo(1L);
    }

    @Test
    @DisplayName("pagar sem Idempotency-Key é 400, não 500")
    void deve_exigir_chave_de_idempotencia() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        mockMvc.perform(post("/api/v1/billings/" + cobranca + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(FINANCEIRO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPagamento("100.00", "BRL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    @DisplayName("pagamento acima do saldo é recusado com o saldo na mensagem")
    void nao_deve_aceitar_pagamento_acima_do_saldo() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        pagar(cobranca, "500.00", "acima-do-saldo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));

        Long pagamentos = jdbc.queryForObject("SELECT count(*) FROM payments", Long.class);
        assertThat(pagamentos).isZero();
    }

    @Test
    @DisplayName("moeda diferente da cobrança é 422, não uma soma sem significado")
    void nao_deve_aceitar_moeda_diferente() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        mockMvc.perform(post("/api/v1/billings/" + cobranca + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(FINANCEIRO))
                        .header("Idempotency-Key", "moeda-errada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPagamento("100.00", "USD")))
                .andExpect(status().isUnprocessableEntity());
    }

    // =================================================================
    // Estorno
    // =================================================================

    @Test
    @DisplayName("estornar devolve o saldo e mantém o pagamento no histórico, marcado")
    void deve_estornar_sem_apagar() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();
        MvcResult pago = pagar(cobranca, "100.00", "sera-estornado")
                .andExpect(status().isCreated()).andReturn();
        String pagamentoId = objectMapper.readTree(pago.getResponse().getContentAsString())
                .get("payments").get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/payments/" + pagamentoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(FINANCEIRO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Pagamento em duplicidade identificado pelo banco"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(100.00))
                .andExpect(jsonPath("$.payments[0].refunded").value(true));

        Long pagamentos = jdbc.queryForObject("SELECT count(*) FROM payments", Long.class);
        assertThat(pagamentos).as("estorno não apaga a linha").isEqualTo(1L);
    }

    @Test
    @DisplayName("gestor não estorna — só payment:refund chega lá")
    void gestor_nao_deve_estornar() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(FINANCEIRO, SENHA, "FINANCE");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();
        MvcResult pago = pagar(cobranca, "100.00", "tentativa-gestor")
                .andExpect(status().isCreated()).andReturn();
        String pagamentoId = objectMapper.readTree(pago.getResponse().getContentAsString())
                .get("payments").get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/payments/" + pagamentoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Quero estornar mas não posso"}
                                """))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // Cancelamento do contrato × cobranças
    // =================================================================

    @Test
    @DisplayName("cancelar o contrato cancela as futuras e deixa as vencidas de pé")
    void cancelar_contrato_nao_deve_perdoar_divida() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String contratoId = umContratoAtivoDe("1200.00");
        LocalDate hoje = LocalDate.now(clock);

        Long vencidasAntes = jdbc.queryForObject(
                "SELECT count(*) FROM billings WHERE due_date < ?", Long.class, hoje);

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Cliente encerrou a operação na filial de SP"}
                                """))
                .andExpect(status().isOk());

        Long canceladas = jdbc.queryForObject(
                "SELECT count(*) FROM billings WHERE status = 'CANCELLED'", Long.class);
        Long vencidasDepois = jdbc.queryForObject(
                "SELECT count(*) FROM billings WHERE due_date < ? AND status <> 'CANCELLED'",
                Long.class, hoje);

        assertThat(canceladas).as("as futuras saem").isPositive();
        assertThat(vencidasDepois)
                .as("cancelar contrato não perdoa dívida")
                .isEqualTo(vencidasAntes);
    }

    // =================================================================
    // Inadimplência
    // =================================================================

    @Test
    @DisplayName("a varredura diária marca como OVERDUE tudo que passou do vencimento")
    void deve_marcar_inadimplencia() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoDe("1200.00");
        LocalDate hoje = LocalDate.now(clock);
        Long esperadas = jdbc.queryForObject(
                "SELECT count(*) FROM billings WHERE due_date < ?", Long.class, hoje);

        int marcadas = markOverdue.execute();

        assertThat(marcadas).isEqualTo(esperadas.intValue());
        Long overdue = jdbc.queryForObject(
                "SELECT count(*) FROM billings WHERE status = 'OVERDUE'", Long.class);
        assertThat(overdue).isEqualTo(esperadas);

        mockMvc.perform(get("/api/v1/billings/overdue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(esperadas.intValue()));
    }

    @Test
    @DisplayName("rodar a varredura duas vezes não gera evento duplicado")
    void a_varredura_deve_ser_idempotente() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoDe("1200.00");
        markOverdue.execute();
        Long eventosApos1 = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'billing.overdue'",
                Long.class);

        int segunda = markOverdue.execute();

        assertThat(segunda).isZero();
        Long eventosApos2 = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'billing.overdue'",
                Long.class);
        assertThat(eventosApos2).isEqualTo(eventosApos1);
    }

    // =================================================================
    // Cobrança avulsa, escopo e permissões
    // =================================================================

    @Test
    @DisplayName("cobrança avulsa é criada sem parcela e sem contrato")
    void deve_criar_cobranca_avulsa() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        String clienteId = umCliente(GESTOR, CNPJ);

        mockMvc.perform(post("/api/v1/billings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "reference": "Multa contratual - atraso set/2026",
                                 "amount": "350.00",
                                 "currency": "BRL",
                                 "dueDate": "%s"}
                                """.formatted(clienteId, LocalDate.now(clock).plusDays(10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.installment").doesNotExist())
                .andExpect(jsonPath("$.contractId").doesNotExist());
    }

    @Test
    @DisplayName("vendedor não enxerga cobrança de outra carteira — 404, nunca 403")
    void vendedor_nao_deve_ver_cobranca_alheia() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        mockMvc.perform(get("/api/v1/billings/" + cobranca)
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a listagem do vendedor vem vazia — ele tem billing:read, não billing:read_all")
    void listagem_do_vendedor_deve_respeitar_a_carteira() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        umContratoAtivoDe("1200.00");

        // As 12 parcelas existem e são do cliente do gestor. O vendedor
        // enxerga a listagem — vazia, porque o filtro sai do token dele.
        mockMvc.perform(get("/api/v1/billings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("vendedor não registra pagamento — não tem payment:create")
    void vendedor_nao_deve_pagar() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");
        umContratoAtivoDe("1200.00");
        String cobranca = primeiraCobranca();

        mockMvc.perform(post("/api/v1/billings/" + cobranca + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .header("Idempotency-Key", "vendedor-tentando")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPagamento("100.00", "BRL")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem token não passa nem na porta")
    void deve_exigir_autenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/billings"))
                .andExpect(status().isUnauthorized());
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

    /**
     * Contrato mensal de 12 meses começando há 6, para que metade das parcelas
     * já esteja vencida e a outra metade no futuro — é o que os testes de
     * inadimplência e de cancelamento precisam.
     */
    private String umContratoAtivoDe(String valor) throws Exception {
        String clienteId = umCliente(GESTOR, CNPJ);
        LocalDate inicio = LocalDate.now(clock).minusMonths(6).withDayOfMonth(1);
        LocalDate fim = inicio.plusYears(1).minusDays(1);

        MvcResult criado = mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "number": "CT-2026-0042",
                                 "title": "Licença Enterprise",
                                 "startDate": "%s",
                                 "endDate": "%s",
                                 "amount": "%s",
                                 "currency": "BRL",
                                 "billingCycle": "MONTHLY",
                                 "autoRenew": false}
                                """.formatted(clienteId, inicio, fim, valor)))
                .andExpect(status().isCreated())
                .andReturn();

        String contratoId = objectMapper.readTree(criado.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());
        return contratoId;
    }

    /** A parcela 1, que é a de vencimento mais antigo. */
    private String primeiraCobranca() {
        List<String> ids = jdbc.queryForList(
                "SELECT id::text FROM billings ORDER BY installment LIMIT 1", String.class);
        assertThat(ids).as("o contrato ativado precisa ter gerado parcelas").isNotEmpty();
        return ids.get(0);
    }

    private org.springframework.test.web.servlet.ResultActions pagar(
            String cobrancaId, String valor, String chave) throws Exception {
        return mockMvc.perform(post("/api/v1/billings/" + cobrancaId + "/payments")
                .header(HttpHeaders.AUTHORIZATION, bearer(FINANCEIRO))
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoPagamento(valor, "BRL")));
    }

    private static String corpoPagamento(String valor, String moeda) {
        return """
                {"amount": "%s", "currency": "%s", "method": "PIX",
                 "paidAt": "2026-09-01T10:33:00Z"}
                """.formatted(valor, moeda);
    }
}
