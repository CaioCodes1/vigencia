package com.caiocodes.crbap.iam.infrastructure.security;

import com.caiocodes.crbap.iam.application.port.TokenDenylist;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Denylist de access tokens revogados, no Redis.
 *
 * <p>O TTL de cada entrada é exatamente o tempo que falta para o token expirar:
 * depois disso ele já não vale por si, e a chave some sozinha. A lista nunca
 * cresce sem limite — isso é projeto, não sorte.
 *
 * <p><b>Esta é a única função do Redis que falha fechado.</b> Se o Redis cair,
 * cache e dedupe apenas degradam; aqui, não conseguir saber se um token foi
 * revogado tem que significar "recuse", nunca "deixe passar". Tirar acesso de
 * quem foi demitido é mais importante que manter a API respondendo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenDenylist implements TokenDenylist {

    private static final String KEY_PREFIX = "jwt:denied:";

    private final StringRedisTemplate redis;

    @Override
    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
        } catch (RuntimeException e) {
            log.error("Redis indisponível na consulta da denylist — recusando o token "
                    + "por precaução (falha fechada). jti={}", jti, e);
            return true;
        }
    }
}
