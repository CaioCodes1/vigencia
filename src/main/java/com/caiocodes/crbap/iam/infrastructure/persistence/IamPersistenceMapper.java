package com.caiocodes.crbap.iam.infrastructure.persistence;

import com.caiocodes.crbap.iam.domain.RefreshToken;
import com.caiocodes.crbap.iam.domain.Role;
import com.caiocodes.crbap.iam.domain.RoleId;
import com.caiocodes.crbap.iam.domain.User;
import com.caiocodes.crbap.iam.domain.UserId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Tradução entre o modelo de persistência e o de domínio.
 *
 * <p>Escrito à mão, e não com MapStruct: os agregados não têm setter nem
 * construtor público completo (é justamente esse o ponto deles), então o
 * gerador não teria por onde entrar. São 40 linhas explícitas — mais legíveis
 * que a configuração que faria o MapStruct chegar no mesmo lugar.
 */
@Component
public class IamPersistenceMapper {

    public User toDomain(UserEntity entity) {
        return User.rehydrate(
                UserId.of(entity.getId()),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.isActive(),
                entity.getFailedLoginAttempts(),
                entity.getLockedUntil(),
                entity.getLastLoginAt(),
                entity.getPasswordChangedAt(),
                entity.isMustChangePassword(),
                entity.getRoles().stream().map(this::toDomain).collect(Collectors.toSet()),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    public Role toDomain(RoleEntity entity) {
        Set<String> permissions = entity.getPermissions().stream()
                .map(PermissionEntity::getName)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return Role.of(RoleId.of(entity.getId()), entity.getName(), entity.getDescription(),
                entity.isSystemRole(), permissions);
    }

    /**
     * Copia o estado do agregado para uma entidade já existente (ou nova).
     * {@code createdAt} e {@code version} não são tocados: quem cuida deles é o
     * Hibernate.
     */
    public void copyToEntity(User user, UserEntity entity, Set<RoleEntity> roles) {
        entity.setId(user.id().value());
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        entity.setFullName(user.fullName());
        entity.setActive(user.isActive());
        entity.setFailedLoginAttempts((short) user.failedLoginAttempts());
        entity.setLockedUntil(user.lockedUntil());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setPasswordChangedAt(user.passwordChangedAt());
        entity.setMustChangePassword(user.mustChangePassword());
        entity.getRoles().clear();
        entity.getRoles().addAll(roles);
    }

    public RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.rehydrate(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getTokenHash(),
                entity.getFamilyId(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt(),
                entity.getUserAgent(),
                entity.getIpAddress());
    }

    public void copyToEntity(RefreshToken token, RefreshTokenEntity entity) {
        entity.setId(token.id());
        entity.setUserId(token.userId().value());
        entity.setTokenHash(token.tokenHash());
        entity.setFamilyId(token.familyId());
        entity.setExpiresAt(token.expiresAt());
        entity.setUsedAt(token.usedAt());
        entity.setRevokedAt(token.revokedAt());
        entity.setUserAgent(token.userAgent());
        entity.setIpAddress(token.ipAddress());
    }
}
