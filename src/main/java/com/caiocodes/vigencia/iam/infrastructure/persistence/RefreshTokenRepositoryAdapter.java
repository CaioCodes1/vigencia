package com.caiocodes.vigencia.iam.infrastructure.persistence;

import com.caiocodes.vigencia.iam.domain.RefreshToken;
import com.caiocodes.vigencia.iam.domain.RefreshTokenRepository;
import com.caiocodes.vigencia.iam.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;
    private final IamPersistenceMapper mapper;

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    /** Mesma escolha do adaptador de usuários: devolve o agregado recebido. */
    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = jpa.findById(token.id()).orElseGet(RefreshTokenEntity::new);
        mapper.copyToEntity(token, entity);
        jpa.save(entity);
        return token;
    }

    @Override
    public int revokeFamily(UUID familyId, Instant now) {
        return jpa.revokeFamily(familyId, now);
    }

    @Override
    public int revokeAllOfUser(UserId userId, Instant now) {
        return jpa.revokeAllOfUser(userId.value(), now);
    }

    @Override
    public int deleteExpiredBefore(Instant limit) {
        return jpa.deleteExpiredBefore(limit);
    }
}
