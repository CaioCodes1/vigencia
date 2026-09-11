package com.caiocodes.vigencia.iam.application;

import com.caiocodes.vigencia.iam.application.port.TokenDenylist;
import com.caiocodes.vigencia.iam.domain.RefreshTokenRepository;
import com.caiocodes.vigencia.iam.domain.TokenHash;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encerra a sessão: revoga o refresh e coloca o access token na denylist.
 *
 * <p>Os dois passos são necessários. Só revogar o refresh deixaria o access
 * token valendo por até 15 minutos — tempo de sobra para quem estiver com ele.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final TokenDenylist denylist;
    private final Clock clock;

    @Transactional
    public void execute(LogoutCommand command) {
        Instant now = clock.instant();

        if (command.refreshToken() != null && !command.refreshToken().isBlank()) {
            refreshTokens.findByTokenHash(TokenHash.of(command.refreshToken()))
                    .ifPresent(token -> {
                        // Derruba a família toda: logout é "encerre esta sessão",
                        // e a sessão é a cadeia inteira de rotações.
                        refreshTokens.revokeFamily(token.familyId(), now);
                        log.info("auth.logout userId={} familia={}",
                                token.userId(), token.familyId());
                    });
        }

        if (command.accessTokenId() != null) {
            denylist.revoke(command.accessTokenId(), command.accessTokenExpiresAt());
        }
    }
}
