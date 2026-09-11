package com.caiocodes.vigencia.iam.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência dos refresh tokens. */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    /**
     * Revoga a família inteira de uma vez. É a reação à detecção de reuso:
     * precisa ser uma operação só, para não deixar janela entre a descoberta do
     * roubo e o corte da sessão.
     */
    int revokeFamily(UUID familyId, Instant now);

    int revokeAllOfUser(UserId userId, Instant now);

    /** Limpeza do job diário: token expirado não serve nem de histórico. */
    int deleteExpiredBefore(Instant limit);
}
