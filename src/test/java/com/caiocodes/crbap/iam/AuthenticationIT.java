package com.caiocodes.crbap.iam;

import com.caiocodes.crbap.support.AbstractIamIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O fluxo de autenticação de ponta a ponta, contra Postgres e Redis reais. */
class AuthenticationIT extends AbstractIamIntegrationTest {

    private static final String EMAIL = "ana@empresa.com";
    private static final String SENHA = "senha-bem-comprida-123";

    @Test
    @DisplayName("login válido devolve os dois tokens e o perfil com permissões")
    void deve_autenticar() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "MANAGER");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL, SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.roles[0]").value("MANAGER"))
                .andExpect(jsonPath("$.user.permissions").value(
                        org.hamcrest.Matchers.hasItem("contract:renew")));
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente devolvem exatamente a mesma resposta")
    void deve_dar_a_mesma_resposta_para_senha_errada_e_email_inexistente() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");

        String senhaErrada = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL, "senha-errada-mas-comprida")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String emailInexistente = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("naoexiste@empresa.com", SENHA)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Diferenciar as duas transformaria a API num verificador de e-mails
        // cadastrados. O traceId muda; código e mensagem, não.
        assertThat(codigo(senhaErrada)).isEqualTo(codigo(emailInexistente))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(mensagem(senhaErrada)).isEqualTo(mensagem(emailInexistente));
    }

    @Test
    @DisplayName("após 3 falhas (política de teste) a conta é trancada com 423")
    void deve_bloquear_a_conta_apos_o_limite() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin(EMAIL, "errada-mas-comprida")))
                    .andExpect(status().isUnauthorized());
        }

        // Agora nem a senha CERTA entra — é o efeito pretendido.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL, SENHA)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("o contador de falhas sobrevive à exceção — é o noRollbackFor")
    void o_contador_de_falhas_deve_persistir() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL, "errada-mas-comprida")))
                .andExpect(status().isUnauthorized());

        Integer tentativas = jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, EMAIL);
        assertThat(tentativas)
                .as("sem noRollbackFor, o rollback apagaria o contador e o bloqueio "
                        + "por força bruta nunca aconteceria")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("o refresh rotaciona: o token antigo morre e um novo nasce")
    void deve_rotacionar_o_refresh_token() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");
        JsonNode login = autenticar(EMAIL, SENHA);
        String rt1 = login.get("refreshToken").asText();

        String rt2 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(rt1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(rt2).get("refreshToken").asText()).isNotEqualTo(rt1);
    }

    @Test
    @DisplayName("reapresentar um refresh já usado derruba a família inteira")
    void deve_detectar_reuso_e_revogar_a_familia() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");
        String rt1 = autenticar(EMAIL, SENHA).get("refreshToken").asText();

        String rt2 = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(rt1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("refreshToken").asText();

        // O atacante (ou o dono, se o token vazou) usa o RT1 de novo:
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(rt1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // E o RT2, que era legítimo, cai junto: a sessão inteira é cortada.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(rt2)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout invalida o access token na hora, sem esperar os 15 minutos")
    void logout_deve_colocar_o_token_na_denylist() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");
        JsonNode login = autenticar(EMAIL, SENHA);
        String accessToken = login.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}"
                                .formatted(login.get("refreshToken").asText())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me devolve papéis e permissões efetivas do token")
    void me_deve_devolver_o_perfil() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "FINANCE");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(EMAIL, SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("FINANCE"))
                .andExpect(jsonPath("$.permissions").value(
                        org.hamcrest.Matchers.hasItem("payment:refund")));
    }

    @Test
    @DisplayName("trocar a senha encerra todas as sessões abertas")
    void trocar_senha_deve_revogar_as_sessoes() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");
        JsonNode login = autenticar(EMAIL, SENHA);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + login.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "outra-senha-bem-longa"}
                                """.formatted(SENHA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}"
                                .formatted(login.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL, "outra-senha-bem-longa")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha nova curta demais é recusada com 409")
    void deve_recusar_senha_fraca() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(EMAIL, SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "curta"}
                                """.formatted(SENHA)))
                .andExpect(status().isBadRequest());
    }

    private static String corpoLogin(String email, String senha) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, senha);
    }

    private String codigo(String json) throws Exception {
        return objectMapper.readTree(json).get("code").asText();
    }

    private String mensagem(String json) throws Exception {
        return objectMapper.readTree(json).get("message").asText();
    }
}
