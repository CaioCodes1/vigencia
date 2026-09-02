package com.caiocodes.crbap.iam.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT e da política de autenticação, vinda do ambiente.
 *
 * <p>As chaves chegam em PEM por variável de ambiente — nunca versionadas.
 * Quando ausentes, o {@link JwtKeyProvider} gera um par efêmero, e isso é
 * permitido <b>apenas</b> fora de produção.
 */
@ConfigurationProperties(prefix = "crbap.security")
public record JwtProperties(
        String issuer,
        String audience,
        String privateKey,
        String publicKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        int maxFailedAttempts,
        Duration lockDuration,
        int loginAttemptsPerMinute) {

    public JwtProperties {
        issuer = issuer == null ? "crbap" : issuer;
        audience = audience == null ? "crbap-api" : audience;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        maxFailedAttempts = maxFailedAttempts == 0 ? 5 : maxFailedAttempts;
        lockDuration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
        loginAttemptsPerMinute = loginAttemptsPerMinute == 0 ? 5 : loginAttemptsPerMinute;
    }
}
