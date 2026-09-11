package com.caiocodes.vigencia.iam.infrastructure.security;

import com.caiocodes.vigencia.shared.application.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Implementação da porta {@link CurrentUser} lendo o {@code SecurityContext}.
 *
 * <p>É o único ponto do sistema que sabe que existe Spring Security por trás da
 * pergunta "quem está chamando?". Os casos de uso dos outros módulos dependem
 * só da interface — e no teste unitário recebem um dublê de duas linhas.
 */
@Component
public class SecurityContextCurrentUser implements CurrentUser {

    @Override
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AuthenticatedUser user) {
            return user.userId().value();
        }
        // @WithMockUser e afins: autenticado, mas sem id de usuário real.
        return null;
    }

    @Override
    public boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permission::equals);
    }
}
