package com.caiocodes.vigencia.iam.infrastructure.security;

import com.caiocodes.vigencia.iam.domain.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * O usuário autenticado, do jeito que o Spring Security entende.
 *
 * <p>Guarda também o {@code jti} e a expiração do token porque o logout precisa
 * deles para colocar exatamente <i>este</i> token na denylist — sem obrigar o
 * controller a decodificar o header de novo.
 */
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final UserId userId;
    private final String tokenId;
    private final Instant tokenExpiresAt;

    public AuthenticatedUser(UserId userId, String tokenId, Instant tokenExpiresAt,
                             Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.tokenId = tokenId;
        this.tokenExpiresAt = tokenExpiresAt;
        setAuthenticated(true);
    }

    /** Atalho para os controllers: quem está chamando agora? */
    public static AuthenticatedUser current() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication();
    }

    public UserId userId() {
        return userId;
    }

    public String tokenId() {
        return tokenId;
    }

    public Instant tokenExpiresAt() {
        return tokenExpiresAt;
    }

    public Set<String> permissions() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
