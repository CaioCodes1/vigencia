package com.caiocodes.vigencia.iam.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token: string aleatória opaca, guardada no banco apenas como hash.
 *
 * <p>Duas ideias sustentam o desenho:
 *
 * <ul>
 *   <li><b>Rotação</b> — cada uso invalida este token e emite outro. Assim, um
 *       token vazado tem validade de um único uso.</li>
 *   <li><b>Família</b> — todos os tokens descendentes de um login compartilham
 *       o {@code familyId}. Se um token <b>já usado</b> aparecer de novo, é
 *       porque alguém tem uma cópia: revoga-se a família inteira e o usuário
 *       refaz o login. Sem isso, o atacante e o dono da conta renovariam a
 *       sessão em paralelo, indefinidamente.</li>
 * </ul>
 */
public class RefreshToken {

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final UUID familyId;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final String userAgent;
    private final String ipAddress;
    private Instant usedAt;
    private Instant revokedAt;

    private RefreshToken(UUID id, UserId userId, String tokenHash, UUID familyId,
                         Instant expiresAt, Instant createdAt, String userAgent,
                         String ipAddress) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.familyId = Objects.requireNonNull(familyId);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = createdAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    /** Primeiro token de uma sessão: abre uma família nova. */
    public static RefreshToken issue(UserId userId, String tokenHash, Duration ttl,
                                     Instant now, String userAgent, String ipAddress) {
        return new RefreshToken(UUID.randomUUID(), userId, tokenHash, UUID.randomUUID(),
                now.plus(ttl), now, userAgent, ipAddress);
    }

    /** Sucessor na rotação: mesma família do token que acabou de ser usado. */
    public RefreshToken rotate(String newTokenHash, Duration ttl, Instant now) {
        return new RefreshToken(UUID.randomUUID(), userId, newTokenHash, familyId,
                now.plus(ttl), now, userAgent, ipAddress);
    }

    public static RefreshToken rehydrate(UUID id, UserId userId, String tokenHash, UUID familyId,
                                         Instant expiresAt, Instant usedAt, Instant revokedAt,
                                         Instant createdAt, String userAgent, String ipAddress) {
        RefreshToken token = new RefreshToken(id, userId, tokenHash, familyId, expiresAt,
                createdAt, userAgent, ipAddress);
        token.usedAt = usedAt;
        token.revokedAt = revokedAt;
        return token;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsable(Instant now) {
        return !isUsed() && !isRevoked() && !isExpired(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public UUID familyId() {
        return familyId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String userAgent() {
        return userAgent;
    }

    public String ipAddress() {
        return ipAddress;
    }
}
