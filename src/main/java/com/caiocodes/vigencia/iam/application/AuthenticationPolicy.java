package com.caiocodes.vigencia.iam.application;

import java.time.Duration;

/**
 * Os números da política de autenticação, isolados em um objeto.
 *
 * <p>Declarado aqui, na camada de aplicação, e não como
 * {@code @ConfigurationProperties} na infraestrutura: assim o caso de uso
 * depende de um record simples e o teste passa os valores no construtor, sem
 * subir contexto do Spring. Quem lê o {@code application.yml} e monta este
 * objeto é uma classe de configuração da infra.
 *
 * @param maxFailedAttempts tentativas erradas seguidas antes de trancar
 * @param lockDuration      por quanto tempo a conta fica trancada
 * @param accessTokenTtl    validade do access token — curta de propósito
 * @param refreshTokenTtl   validade do refresh token
 */
public record AuthenticationPolicy(
        int maxFailedAttempts,
        Duration lockDuration,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    public AuthenticationPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts deve ser >= 1");
        }
        if (accessTokenTtl.compareTo(refreshTokenTtl) > 0) {
            throw new IllegalArgumentException(
                    "O access token não pode durar mais que o refresh token");
        }
    }
}
