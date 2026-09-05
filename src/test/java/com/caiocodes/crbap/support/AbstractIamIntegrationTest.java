package com.caiocodes.crbap.support;

import com.caiocodes.crbap.iam.application.port.PasswordHasher;
import com.caiocodes.crbap.iam.domain.Role;
import com.caiocodes.crbap.iam.domain.RoleRepository;
import com.caiocodes.crbap.iam.domain.User;
import com.caiocodes.crbap.iam.domain.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base dos testes de integração que precisam de usuários reais no banco.
 *
 * <p>Limpa as tabelas de IAM antes de cada teste em vez de usar
 * {@code @Transactional} com rollback: o fluxo de autenticação depende de
 * commits reais (o contador de tentativas usa {@code noRollbackFor}), e um teste
 * embrulhado numa transação esconderia justamente isso.
 *
 * <p>As tabelas de papéis e permissões <b>não</b> são limpas: vêm da migração
 * repetível {@code R__seed_rbac.sql} e são a matriz de verdade do sistema.
 *
 * <p><b>A limpeza é toda daqui, e a ordem importa.</b> Antes da fase 5 cada
 * classe apagava as suas tabelas num {@code @BeforeEach} próprio — o que parou
 * de funcionar quando cobranças passaram a referenciar contratos, clientes e
 * usuários com {@code ON DELETE RESTRICT}: o JUnit roda o {@code @BeforeEach} da
 * superclasse <b>antes</b> do da subclasse, então a base apagava usuários e o
 * banco recusava. Com tudo num lugar só, a ordem é explícita e cada classe de
 * teste começa do zero.
 */
public abstract class AbstractIamIntegrationTest extends AbstractIntegrationTest {

    @Autowired protected UserRepository users;
    @Autowired protected RoleRepository roles;
    @Autowired protected PasswordHasher passwordHasher;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected Clock clock;
    @Autowired protected CacheManager cacheManager;

    @BeforeEach
    void limparBase() {
        // O Redis nao e limpo pelo DELETE das tabelas. Sem isto, o painel
        // calculado por um teste seria servido do cache para o proximo, que
        // acabou de zerar a base — e a falha apareceria em um teste que nao
        // tem nada a ver com cache. Aconteceu nesta fase.
        cacheManager.getCacheNames().forEach(nome -> {
            var cache = cacheManager.getCache(nome);
            if (cache != null) {
                cache.clear();
            }
        });
        // TRUNCATE, e não DELETE: o gatilho da V6 recusa DELETE em audit_logs,
        // e é para isso que ele existe. TRUNCATE não dispara gatilho de linha —
        // é a porta deixada aberta de propósito, para expurgo por retenção e
        // para a limpeza entre testes. Em produção quem a fecha é o REVOKE.
        jdbc.update("TRUNCATE TABLE audit_logs");

        // Dos filhos para os pais: pagamento depende de cobrança, que depende de
        // contrato e cliente, que dependem de usuário. Inverter a ordem faz o
        // Postgres recusar o DELETE, e o erro fala de constraint, não de teste.
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM billings");
        jdbc.update("DELETE FROM contracts");
        jdbc.update("DELETE FROM client_contacts");
        jdbc.update("DELETE FROM clients");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
    }

    protected User dadoUmUsuario(String email, String senha, String papel) {
        User user = User.create(email, passwordHasher.hash(senha), "Usuário de Teste",
                clock.instant(), false);
        if (papel != null) {
            Role role = roles.findByName(papel).orElseThrow(
                    () -> new IllegalStateException("Papel não encontrado no seed: " + papel));
            user.assignRole(role);
        }
        return users.save(user);
    }

    /** Faz login pela API e devolve o corpo da resposta já parseado. */
    protected JsonNode autenticar(String email, String senha) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, senha)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String tokenDe(String email, String senha) throws Exception {
        return autenticar(email, senha).get("accessToken").asText();
    }
}
