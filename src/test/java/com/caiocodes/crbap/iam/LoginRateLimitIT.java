package com.caiocodes.crbap.iam;

import com.caiocodes.crbap.support.AbstractIamIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit do login.
 *
 * <p>Limite baixado para 3 só neste contexto: com o valor de produção, o teste
 * precisaria de dezenas de chamadas e ficaria lento e ilegível.
 */
@TestPropertySource(properties = "crbap.security.login-attempts-per-minute=3")
class LoginRateLimitIT extends AbstractIamIntegrationTest {

    private static final String EMAIL = "ana@empresa.com";
    private static final String SENHA = "senha-bem-comprida-123";

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void limparContadores() {
        Set<String> chaves = redis.keys("ratelimit:*");
        if (chaves != null && !chaves.isEmpty()) {
            redis.delete(chaves);
        }
    }

    @Test
    @DisplayName("a quarta tentativa no mesmo minuto leva 429 com Retry-After")
    void deve_limitar_tentativas_de_login() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("errada-mas-comprida")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("errada-mas-comprida")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("o limite vale mesmo com a senha certa — ele conta requisições, não erros")
    void deve_limitar_independente_do_resultado() throws Exception {
        dadoUmUsuario(EMAIL, SENHA, "SALES");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SENHA)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(SENHA)))
                .andExpect(status().isTooManyRequests());
    }

    private static String corpo(String senha) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, senha);
    }
}
