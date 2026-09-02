package com.caiocodes.crbap.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração (*IT).
 *
 * <p>Sobe um PostgreSQL 16 e um Redis 7 de verdade, em container.
 * <b>Por que não H2:</b> H2 não é PostgreSQL — não tem JSONB, não tem citext,
 * não tem índice único parcial, não tem {@code FILTER (WHERE ...)} e trata NULL
 * na unicidade de outro jeito. Metade das decisões deste schema seria "testada"
 * contra um banco que não as suporta, e um teste verde que fica vermelho em
 * produção é pior que teste nenhum.
 *
 * <p>Os containers são <b>singletons</b>: iniciados uma vez no primeiro
 * carregamento da classe e reaproveitados por toda a suíte, em vez de subir e
 * descer a cada classe de teste. O Ryuk derruba tudo quando a JVM sai.
 *
 * <p><b>Pré-requisito nesta máquina</b> (Engine 29 + docker-java antigo):
 * {@code ~/.docker-java.properties} com {@code api.version=1.44} e a variável
 * {@code DOCKER_HOST=npipe:////./pipe/docker_engine_linux}. Sem os dois, todo IT
 * morre com "Could not find a valid Docker environment" mesmo com o
 * {@code docker run} funcionando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("crbap")
                    .withUsername("crbap")
                    .withPassword("crbap");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }
}
