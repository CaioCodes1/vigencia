package com.caiocodes.crbap.iam.infrastructure.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT e da política de autenticação, vinda do ambiente.
 *
 * <p>As chaves chegam em PEM por variável de ambiente — nunca versionadas.
 * Quando ausentes, o {@link JwtKeyProvider} gera um par efêmero, e isso é
 * permitido <b>apenas</b> fora de produção.
 *
 * <p>{@code internalNetworks} não tem nada de JWT, mas mora aqui porque esta é
 * a classe de {@code crbap.security}: uma segunda classe de propriedades para
 * uma lista seria cerimônia. Ver {@link InternalNetworkAuthorizationManager}.
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
        int loginAttemptsPerMinute,
        List<String> internalNetworks) {

    public JwtProperties {
        issuer = issuer == null ? "crbap" : issuer;
        audience = audience == null ? "crbap-api" : audience;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        maxFailedAttempts = maxFailedAttempts == 0 ? 5 : maxFailedAttempts;
        lockDuration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
        loginAttemptsPerMinute = loginAttemptsPerMinute == 0 ? 5 : loginAttemptsPerMinute;
        // Loopback e as faixas privadas da RFC 1918. A 172.16/12 inclui a rede
        // padrão do Docker, então a pilha de observabilidade do compose raspa
        // as métricas sem nenhum ajuste — e nada de fora da rede lê.
        internalNetworks = internalNetworks == null || internalNetworks.isEmpty()
                ? List.of("127.0.0.1/32", "::1/128",
                          "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
                : List.copyOf(internalNetworks);
    }
}
