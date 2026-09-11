package com.caiocodes.vigencia.observability;

import com.caiocodes.vigencia.shared.application.BusinessMetrics;
import com.caiocodes.vigencia.shared.infrastructure.metrics.BusinessGauges;
import com.caiocodes.vigencia.support.AbstractIamIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 8: o que o Prometheus lê, e quem tem direito de ler. */
class MetricsIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String GESTOR = "gestor@empresa.com";
    private static final String ADMIN = "admin@empresa.com";
    private static final String CNPJ = "11222333000181";

    /** Um endereço público qualquer (bloco de documentação da RFC 5737). */
    private static final String DA_INTERNET = "203.0.113.5";

    @Autowired private BusinessGauges gauges;
    @Autowired private BusinessMetrics metrics;

    // =================================================================
    // Quem pode ler
    // =================================================================

    @Test
    @DisplayName("o coletor da rede interna lê /actuator/prometheus sem token")
    void rede_interna_deve_ler_sem_token() throws Exception {
        // Um Prometheus não renova access token de 15 minutos. Sem esta regra,
        // a alternativa seria uma credencial eterna de administrador num
        // arquivo de configuração.
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("da internet, sem token, as métricas não abrem")
    void de_fora_deve_recusar() throws Exception {
        // As métricas contam quantos contratos a empresa tem e quanto está em
        // atraso. permitAll aqui seria servir isso ao mundo.
        mockMvc.perform(get("/actuator/prometheus").with(deFora()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("de fora, com system:monitor, abre — a rede é uma alternativa, não a única porta")
    void de_fora_com_permissao_deve_abrir() throws Exception {
        dadoUmUsuario(ADMIN, SENHA, "ADMIN");

        mockMvc.perform(get("/actuator/prometheus")
                        .with(deFora())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("o gestor comercial não lê métrica nenhuma")
    void sem_system_monitor_nao_le() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");

        mockMvc.perform(get("/actuator/metrics").with(deFora())
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // O que o Prometheus encontra lá
    // =================================================================

    @Test
    @DisplayName("as quatro métricas de negócio estão expostas, com os valores do banco")
    void deve_expor_as_metricas_de_negocio() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivo();
        // Os gauges são recalculados por tarefa agendada — que está desligada
        // nos testes. Aqui o refresh é explícito, que é o mesmo caminho.
        gauges.atualizar();

        String corpo = raspar();

        assertThat(corpo)
                .contains("vigencia_contracts_active")
                .contains("vigencia_contracts_expiring_30d")
                .contains("vigencia_billings_overdue_amount")
                .contains("vigencia_outbox_pending");
        assertThat(valorDe(corpo, "vigencia_contracts_active"))
                .as("um contrato ativo acabou de ser criado")
                .isEqualTo(1.0);
        assertThat(valorDe(corpo, "vigencia_outbox_pending"))
                .as("criar + ativar + 12 cobranças deixam eventos na fila")
                .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("o carimbo do último sucesso do job vira gauge, com o nome no rótulo")
    void deve_registrar_o_ultimo_sucesso_do_job() throws Exception {
        metrics.jobSucceeded("contract-expiration-scan");

        // É a métrica do alerta "a varredura não roda há mais de 26h" — o aviso
        // de que os avisos pararam, que a planilha nunca teve.
        assertThat(raspar())
                .contains("vigencia_scheduler_last_success_timestamp")
                .contains("job=\"contract-expiration-scan\"");
    }

    @Test
    @DisplayName("o contador de aviso enviado separa sucesso de falha")
    void deve_contar_aviso_por_resultado() throws Exception {
        metrics.notificationSent("CONTRACT_EXPIRING", "EMAIL", true);
        metrics.notificationSent("CONTRACT_EXPIRING", "EMAIL", false);

        String corpo = raspar();
        assertThat(corpo)
                .as("result=failure subindo é o pior modo de falha desta plataforma")
                .contains("result=\"success\"")
                .contains("result=\"failure\"");
    }

    @Test
    @DisplayName("nenhuma métrica leva id como rótulo — cardinalidade derruba o Prometheus")
    void nao_deve_ter_id_como_rotulo() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivo();
        metrics.contractRenewed(new BigDecimal("26400.00"));

        // 10.000 clientes x 5 status = 50.000 séries temporais. Id vai no log,
        // que é indexado por conteúdo; métrica só aceita valor de baixa
        // cardinalidade.
        assertThat(raspar())
                .doesNotContain("contractId=")
                .doesNotContain("clientId=")
                .doesNotContain("userId=");
    }

    // =================================================================
    // Health
    // =================================================================

    @Test
    @DisplayName("o readiness inclui a outbox; o liveness não")
    void readiness_deve_incluir_a_outbox() throws Exception {
        dadoUmUsuario(ADMIN, SENHA, "ADMIN");

        // Com a outbox entupida a aplicação está viva — reiniciá-la não
        // resolveria nada. O que se quer é tirá-la do balanceador.
        mockMvc.perform(get("/actuator/health/readiness")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.outbox.details.pendingEvents").exists());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.outbox").doesNotExist());
    }

    @Test
    @DisplayName("um lote recém-gravado não derruba o health")
    void lote_recente_nao_deve_derrubar_o_health() throws Exception {
        dadoUmUsuario(ADMIN, SENHA, "ADMIN");
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivo();

        // Ativar um contrato anual emite doze eventos de uma vez. A contagem
        // crua passaria do limiar num sistema funcionando perfeitamente; por
        // isso o indicador só conta o que está parado há mais de 5 minutos.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class))
                .isGreaterThan(10L);

        mockMvc.perform(get("/actuator/health/readiness")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.outbox.status").value("UP"))
                .andExpect(jsonPath("$.components.outbox.details.pendingEvents").value(0));
    }

    // =================================================================
    // Apoio
    // =================================================================

    private static RequestPostProcessor deFora() {
        return request -> {
            request.setRemoteAddr(DA_INTERNET);
            return request;
        };
    }

    private String raspar() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** Lê o valor de uma métrica simples no formato de exposição do Prometheus. */
    private static double valorDe(String corpo, String metrica) {
        for (String linha : corpo.split("\n")) {
            if (linha.startsWith(metrica) && !linha.startsWith("#")) {
                return Double.parseDouble(linha.substring(linha.lastIndexOf(' ') + 1).trim());
            }
        }
        throw new AssertionError("métrica não encontrada: " + metrica);
    }

    private String bearer(String email) throws Exception {
        return "Bearer " + tokenDe(email, SENHA);
    }

    private void umContratoAtivo() throws Exception {
        MvcResult cliente = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s",
                                 "legalName": "Acme Comércio Ltda",
                                 "email": "financeiro@acme.com"}
                                """.formatted(CNPJ)))
                .andExpect(status().isCreated())
                .andReturn();
        String clienteId = objectMapper.readTree(cliente.getResponse().getContentAsString())
                .get("id").asText();

        var inicio = java.time.LocalDate.now(clock).minusMonths(6).withDayOfMonth(1);
        MvcResult criado = mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "number": "CT-2026-0042",
                                 "title": "Licença Enterprise",
                                 "startDate": "%s",
                                 "endDate": "%s",
                                 "amount": "1200.00",
                                 "currency": "BRL",
                                 "billingCycle": "MONTHLY",
                                 "autoRenew": false}
                                """.formatted(clienteId, inicio, inicio.plusYears(1).minusDays(1))))
                .andExpect(status().isCreated())
                .andReturn();

        String contratoId = objectMapper.readTree(criado.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(post("/api/v1/contracts/{id}/activate", contratoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());
    }
}
