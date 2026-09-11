package com.caiocodes.vigencia.iam.application;

import com.caiocodes.vigencia.iam.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Visão do usuário para quem está fora do domínio. Sem hash de senha. */
public record UserProfile(
        UUID id,
        String email,
        String fullName,
        boolean active,
        Set<String> roles,
        Set<String> permissions,
        boolean mustChangePassword,
        Instant lastLoginAt) {

    public static UserProfile from(User user) {
        return new UserProfile(
                user.id().value(),
                user.email(),
                user.fullName(),
                user.isActive(),
                user.roleNames(),
                user.effectivePermissions(),
                user.mustChangePassword(),
                user.lastLoginAt());
    }
}
