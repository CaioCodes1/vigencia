package com.caiocodes.vigencia.iam;

import com.caiocodes.vigencia.iam.application.port.AccessTokenIssuer;
import com.caiocodes.vigencia.iam.domain.User;
import com.caiocodes.vigencia.support.AbstractIamIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Os testes que atacam a própria API.
 *
 * <p>A matriz de autorização parametrizada é o que mantém a tabela de
 * "09 — Segurança" verdadeira daqui a um ano: sem ela, a matriz é documentação,
 * e documentação de permissão envelhece sozinha, em silêncio.
 */
class SecurityIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    // =================================================================
    // Autenticação
    // =================================================================

    @Test
    @DisplayName("sem token, endpoint protegido responde 401 — deny by default")
    void sem_token_deve_responder_401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("token com assinatura adulterada é recusado")
    void token_adulterado_deve_responder_401() throws Exception {
        dadoUmUsuario("ana@empresa.com", SENHA, "ADMIN");
        String token = tokenDe("ana@empresa.com", SENHA);
        String adulterado = token.substring(0, token.length() - 6) + "AAAAAA";

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o clássico alg:none não passa")
    void token_sem_assinatura_deve_responder_401() throws Exception {
        String header = base64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64("{\"sub\":\"00000000-0000-0000-0000-000000000001\","
                + "\"iss\":\"vigencia\",\"perms\":[\"user:read\"]}");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + header + "." + payload + "."))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token expirado é recusado")
    void token_expirado_deve_responder_401() throws Exception {
        User user = dadoUmUsuario("ana@empresa.com", SENHA, "ADMIN");
        // Emitido "há uma hora": com TTL de 15 min, já nasceu vencido.
        String expirado = accessTokenIssuer
                .issue(user, Instant.now().minus(1, ChronoUnit.HOURS)).value();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("header sem o prefixo Bearer é ignorado")
    void header_malformado_deve_responder_401() throws Exception {
        dadoUmUsuario("ana@empresa.com", SENHA, "ADMIN");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, tokenDe("ana@empresa.com", SENHA)))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // Autorização — a matriz de "09 — Segurança"
    // =================================================================

    @ParameterizedTest(name = "GET /users como {0} deve responder {1}")
    @CsvSource({
            "ADMIN,       200",
            "AUDITOR,     200",
            "MANAGER,     403",
            "FINANCE,     403",
            "SALES,       403",
            "INTEGRATION, 403"
    })
    void matriz_de_autorizacao_de_usuarios(String papel, int esperado) throws Exception {
        String email = papel.toLowerCase() + "@empresa.com";
        dadoUmUsuario(email, SENHA, papel);

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(email, SENHA)))
                .andExpect(status().is(esperado));
    }

    @Test
    @DisplayName("403 não conta qual permissão faltou")
    void o_403_nao_deve_vazar_o_nome_da_permissao() throws Exception {
        dadoUmUsuario("vendedor@empresa.com", SENHA, "SALES");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenDe("vendedor@empresa.com", SENHA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("user:read"))));
    }

    @Test
    @DisplayName("usuário sem papel nenhum não acessa nada além do próprio perfil")
    void usuario_sem_papel_deve_receber_403() throws Exception {
        dadoUmUsuario("orfao@empresa.com", SENHA, null);
        String token = tokenDe("orfao@empresa.com", SENHA);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // Vazamento de informação
    // =================================================================

    @Test
    @DisplayName("o token não carrega PII: JWT é assinado, não criptografado")
    void o_payload_do_token_nao_deve_conter_pii() throws Exception {
        dadoUmUsuario("ana@empresa.com", SENHA, "MANAGER");
        String token = tokenDe("ana@empresa.com", SENHA);

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThat(payload)
                .doesNotContain("ana@empresa.com")
                .doesNotContain("Usuário de Teste")
                .contains("perms");
    }

    @Test
    @DisplayName("nenhuma resposta de erro devolve stack trace ou nome de pacote")
    void erro_nao_deve_vazar_stacktrace() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("com.caiocodes.vigencia"))));
    }

    @Test
    @DisplayName("o Swagger continua fechado por padrão fora de dev")
    void actuator_sensivel_exige_permissao() throws Exception {
        dadoUmUsuario("vendedor@empresa.com", SENHA, "SALES");

        mockMvc.perform(get("/actuator/env")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenDe("vendedor@empresa.com", SENHA)))
                .andExpect(status().isForbidden());
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
