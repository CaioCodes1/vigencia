package com.caiocodes.vigencia.iam.infrastructure.config;

import com.caiocodes.vigencia.iam.application.AuthenticationPolicy;
import com.caiocodes.vigencia.iam.infrastructure.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a configuração externa (application.yml, variáveis de ambiente) à
 * política que os casos de uso consomem.
 *
 * <p>A tradução existe para o caso de uso não depender de
 * {@code @ConfigurationProperties}: ele recebe um record simples, e o teste
 * unitário monta esse record na mão, sem contexto do Spring.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class IamConfig {

    @Bean
    public AuthenticationPolicy authenticationPolicy(JwtProperties properties) {
        return new AuthenticationPolicy(
                properties.maxFailedAttempts(),
                properties.lockDuration(),
                properties.accessTokenTtl(),
                properties.refreshTokenTtl());
    }
}
