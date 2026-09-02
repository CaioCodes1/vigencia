package com.caiocodes.crbap.iam.infrastructure.security;

import com.caiocodes.crbap.iam.application.port.TokenDenylist;
import com.caiocodes.crbap.iam.domain.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduz o header {@code Authorization: Bearer ...} em uma autenticação do
 * Spring Security.
 *
 * <p>Repare que <b>não há acesso ao banco</b>: papéis e permissões vêm dentro do
 * token assinado. É isso que torna a API stateless e escalável na horizontal.
 *
 * <p>Token inválido não devolve erro daqui: o filtro simplesmente não autentica,
 * e a requisição segue sem credencial — quem responde 401 é o entry point, num
 * lugar só. Filtro que escreve resposta de erro acaba duplicando formato.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final TokenDenylist denylist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Jws<Claims> jws = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
            Claims claims = jws.getPayload();

            if (denylist.isRevoked(claims.getId())) {
                log.warn("auth.token.revogado jti={} rota={}",
                        claims.getId(), request.getRequestURI());
                chain.doFilter(request, response);
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(new AuthenticatedUser(
                    UserId.of(claims.getSubject()),
                    claims.getId(),
                    claims.getExpiration().toInstant(),
                    authorities(claims)));

        } catch (JwtException | IllegalArgumentException e) {
            // Assinatura adulterada, expirado, emissor errado, alg none...
            // Tudo cai aqui e o resultado é o mesmo: segue sem autenticação.
            log.debug("auth.token.invalido rota={} motivo={}",
                    request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> authorities(Claims claims) {
        List<String> permissions = claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class);
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
