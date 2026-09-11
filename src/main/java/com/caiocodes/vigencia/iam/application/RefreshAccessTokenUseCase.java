package com.caiocodes.vigencia.iam.application;

import com.caiocodes.vigencia.iam.application.port.AccessTokenIssuer;
import com.caiocodes.vigencia.iam.application.port.SecureTokenGenerator;
import com.caiocodes.vigencia.iam.domain.RefreshToken;
import com.caiocodes.vigencia.iam.domain.RefreshTokenRepository;
import com.caiocodes.vigencia.iam.domain.TokenHash;
import com.caiocodes.vigencia.iam.domain.User;
import com.caiocodes.vigencia.iam.domain.UserRepository;
import com.caiocodes.vigencia.iam.domain.exception.InvalidRefreshTokenException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Troca um refresh token por um par novo — com rotação e detecção de reuso.
 *
 * <p>O fluxo inteiro está desenhado em "09 — Segurança". Em uma frase: o token
 * apresentado é queimado e um sucessor é emitido na mesma família; se alguém
 * apresentar um token <b>já queimado</b>, é porque existe uma cópia por aí, e a
 * família inteira cai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshAccessTokenUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureTokenGenerator tokenGenerator;
    private final AuthenticationPolicy policy;
    private final Clock clock;

    /** {@code noRollbackFor} pelo mesmo motivo do login: a revogação da família
     *  precisa ser gravada mesmo com a exceção subindo logo em seguida. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult execute(RefreshCommand command) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(TokenHash.of(command.refreshToken()))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isUsed() || current.isRevoked()) {
            int revoked = refreshTokens.revokeFamily(current.familyId(), now);
            log.warn("auth.refresh.reuse_detected userId={} familia={} tokens_revogados={} ip={}",
                    current.userId(), current.familyId(), revoked, command.ipAddress());
            throw new InvalidRefreshTokenException();
        }

        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = users.findById(current.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!user.isActive()) {
            refreshTokens.revokeAllOfUser(user.id(), now);
            log.warn("auth.refresh.usuario_inativo userId={}", user.id());
            throw new InvalidRefreshTokenException();
        }

        current.markUsed(now);
        refreshTokens.save(current);

        String rawRefreshToken = tokenGenerator.generate();
        refreshTokens.save(current.rotate(TokenHash.of(rawRefreshToken),
                policy.refreshTokenTtl(), now));

        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(user, now);
        log.info("auth.refresh.success userId={}", user.id());

        return new AuthResult(accessToken.value(), rawRefreshToken,
                policy.accessTokenTtl().toSeconds(), UserProfile.from(user));
    }
}
