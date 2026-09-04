package com.caiocodes.crbap.notification;

import com.caiocodes.crbap.notification.application.DispatchNotificationsUseCase;
import com.caiocodes.crbap.notification.application.ScanExpiringContractsUseCase;
import com.caiocodes.crbap.support.AbstractIamIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 6 de ponta a ponta: a régua de avisos, o envio e o log. */
class NotificationIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String GESTOR = "gestor@empresa.com";
    private static final String VENDEDOR = "vendedor@empresa.com";
    private static final String CNPJ = "11222333000181";

    @Autowired private ScanExpiringContractsUseCase scanExpiring;
    @Autowired private DispatchNotificationsUseCase dispatch;

    // =================================================================
    // A régua
    // =================================================================

    @Test
    @DisplayName("contrato que vence em exatamente 30 dias gera o aviso D-30")
    void deve_agendar_aviso_de_trinta_dias() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(30));

        int agendados = scanExpiring.execute();

        assertThat(agendados).isPositive();
        List<Integer> janelas = jdbc.queryForList(
                "SELECT days_offset FROM notifications WHERE type = 'CONTRACT_EXPIRING'",
                Integer.class);
        assertThat(janelas).containsOnly(30);
    }

    @Test
    @DisplayName("contrato que vence em 29 dias não entra em janela nenhuma")
    void janela_deve_ser_exata() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(29));

        int agendados = scanExpiring.execute();

        assertThat(agendados).isZero();
        assertThat(quantosAvisos()).isZero();
    }

    @Test
    @DisplayName("rodar a varredura duas vezes NÃO duplica o aviso — quem garante é o índice")
    void a_varredura_deve_ser_idempotente() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(15));
        int primeira = scanExpiring.execute();

        int segunda = scanExpiring.execute();

        assertThat(primeira).isPositive();
        assertThat(segunda).as("o segundo INSERT esbarra em uk_notif_contract_window").isZero();
        assertThat(quantosAvisos()).isEqualTo((long) primeira);
    }

    @Test
    @DisplayName("o aviso vai para o cliente E para o gestor da conta — duas linhas")
    void deve_avisar_cliente_e_gestor() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(7));

        scanExpiring.execute();

        List<String> destinatarios = jdbc.queryForList(
                "SELECT recipient FROM notifications ORDER BY recipient", String.class);
        assertThat(destinatarios).containsExactlyInAnyOrder("financeiro@acme.com", GESTOR);
    }

    @Test
    @DisplayName("o payload guarda o conteúdo formatado, congelado no agendamento")
    void deve_congelar_o_conteudo() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        LocalDate fim = LocalDate.now(clock).plusDays(30);
        umContratoAtivoQueVenceEm(fim);

        scanExpiring.execute();

        String numero = jdbc.queryForObject(
                "SELECT payload ->> 'contractNumber' FROM notifications LIMIT 1", String.class);
        String dataFormatada = jdbc.queryForObject(
                "SELECT payload ->> 'endDateFormatted' FROM notifications LIMIT 1", String.class);
        assertThat(numero).isEqualTo("CT-2026-0042");
        assertThat(dataFormatada)
                .isEqualTo(fim.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    // =================================================================
    // Envio
    // =================================================================

    @Test
    @DisplayName("o job de envio marca como SENT e grava o horário")
    void deve_enviar_os_pendentes() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(30));
        int agendados = scanExpiring.execute();

        int enviados = dispatch.execute();

        assertThat(enviados).isEqualTo(agendados);
        Long pendentes = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE status = 'PENDING'", Long.class);
        Long comHorario = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE status = 'SENT' AND sent_at IS NOT NULL",
                Long.class);
        assertThat(pendentes).isZero();
        assertThat(comHorario).isEqualTo((long) agendados);
    }

    @Test
    @DisplayName("rodar o envio de novo não reenvia o que já saiu")
    void o_envio_deve_ser_idempotente() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(30));
        scanExpiring.execute();
        dispatch.execute();

        int segunda = dispatch.execute();

        assertThat(segunda).isZero();
        Long tentativas = jdbc.queryForObject(
                "SELECT max(attempts) FROM notifications", Long.class);
        assertThat(tentativas).as("uma tentativa por aviso, não duas").isEqualTo(1L);
    }

    // =================================================================
    // O log de envio — "vocês me avisaram?"
    // =================================================================

    @Test
    @DisplayName("a consulta devolve o aviso com o conteúdo que saiu")
    void deve_consultar_o_log_de_envio() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(30));
        scanExpiring.execute();
        dispatch.execute();

        mockMvc.perform(get("/api/v1/notifications?type=CONTRACT_EXPIRING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.content[0].daysOffset").value(30))
                .andExpect(jsonPath("$.content[0].payload.contractNumber")
                        .value("CT-2026-0042"));
    }

    @Test
    @DisplayName("reenvio manual devolve o aviso que falhou para a fila")
    void deve_reenviar_o_que_falhou() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(30));
        scanExpiring.execute();
        // Simula a desistência: é o estado em que o reenvio manual existe para
        // resolver, e o único a partir do qual ele é permitido.
        jdbc.update("UPDATE notifications SET status = 'FAILED', attempts = 4, "
                + "last_error = 'SMTP fora do ar'");
        String id = jdbc.queryForObject("SELECT id::text FROM notifications LIMIT 1",
                String.class);

        mockMvc.perform(post("/api/v1/notifications/" + id + "/resend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));

        assertThat(dispatch.execute()).isEqualTo(1);
    }

    @Test
    @DisplayName("não reenvia um aviso que já saiu")
    void nao_deve_reenviar_o_que_foi_enviado() throws Exception {
        dadoUmUsuario(GESTOR, SENHA, "MANAGER");
        umContratoAtivoQueVenceEm(LocalDate.now(clock).plusDays(30));
        scanExpiring.execute();
        dispatch.execute();
        String id = jdbc.queryForObject("SELECT id::text FROM notifications LIMIT 1",
                String.class);

        mockMvc.perform(post("/api/v1/notifications/" + id + "/resend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("vendedor não tem notification:read — 403")
    void vendedor_nao_deve_ler_o_log() throws Exception {
        dadoUmUsuario(VENDEDOR, SENHA, "SALES");

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem token não passa nem na porta")
    void deve_exigir_autenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // Auxiliares
    // =================================================================

    private String bearer(String email) throws Exception {
        return "Bearer " + tokenDe(email, SENHA);
    }

    /**
     * Cliente + contrato + ativação, com o fim na data pedida.
     *
     * <p>O contato principal é criado junto: sem ele o aviso iria só para o
     * e-mail do cadastro, e o teste de "cliente E gestor" não provaria nada.
     */
    private void umContratoAtivoQueVenceEm(LocalDate fim) throws Exception {
        MvcResult cliente = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s",
                                 "legalName": "Acme Comércio Ltda",
                                 "email": "cadastro@acme.com",
                                 "contacts": [{"name": "Maria",
                                               "email": "financeiro@acme.com",
                                               "primary": true}]}
                                """.formatted(CNPJ)))
                .andExpect(status().isCreated())
                .andReturn();
        String clienteId = objectMapper.readTree(cliente.getResponse().getContentAsString())
                .get("id").asText();

        // O gestor tem client:read_all, então o cliente nasce sem carteira. Para
        // o aviso chegar ao gestor, a carteira precisa apontar para ele.
        jdbc.update("UPDATE clients SET account_manager_id = "
                + "(SELECT id FROM users WHERE email = ?)", GESTOR);

        MvcResult contrato = mockMvc.perform(post("/api/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId": "%s",
                                 "number": "CT-2026-0042",
                                 "title": "Licença Enterprise",
                                 "startDate": "2026-01-01",
                                 "endDate": "%s",
                                 "amount": "24000.00",
                                 "currency": "BRL",
                                 "billingCycle": "YEARLY",
                                 "autoRenew": false}
                                """.formatted(clienteId, fim)))
                .andExpect(status().isCreated())
                .andReturn();
        String contratoId = objectMapper.readTree(contrato.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/v1/contracts/" + contratoId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(GESTOR)))
                .andExpect(status().isOk());
    }

    private Long quantosAvisos() {
        return jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class);
    }
}
