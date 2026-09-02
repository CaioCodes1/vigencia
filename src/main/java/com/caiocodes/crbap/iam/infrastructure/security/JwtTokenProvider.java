package com.caiocodes.crbap.iam.infrastructure.security;

import com.caiocodes.crbap.iam.application.port.AccessTokenIssuer;
import com.caiocodes.crbap.iam.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Emite e valida o access token.
 *
 * <p>O token carrega as permissões efetivas do usuário, o que permite autorizar
 * <b>sem ir ao banco</b> em nenhuma requisição. O preço está registrado na
 * ADR-004: revogar uma permissão só surte efeito no próximo token (até 15 min).
 * Para o caso grave — demissão —, existe a denylist do logout.
 *
 * <p>Nada de PII aqui dentro. JWT é <b>assinado, não criptografado</b>: qualquer
 * um decodifica o payload em base64. Vai o id do usuário, não o nome nem o
 * e-mail.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements AccessTokenIssuer {

    static final String CLAIM_PERMISSIONS = "perms";
    static final String CLAIM_ROLES = "roles";

    private final JwtKeyProvider keys;
    private final JwtProperties properties;

    @Override
    public IssuedAccessToken issue(User user, Instant now) {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        String token = Jwts.builder()
                .id(jti)
                .subject(user.id().toString())
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_PERMISSIONS, List.copyOf(user.effectivePermissions()))
                .claim(CLAIM_ROLES, List.copyOf(user.roleNames()))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();

        return new IssuedAccessToken(token, jti, expiresAt);
    }

    /**
     * Valida assinatura, expiração, emissor e destinatário.
     *
     * <p>A validação do {@code aud} não é decoração: sem ela, um token emitido
     * por outro sistema que use a mesma chave seria aceito aqui. E a biblioteca
     * recusa {@code alg: none} por construção — foi um dos ataques mais
     * explorados contra JWT.
     */
    public Jws<Claims> parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(keys.publicKey())
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token);

        if (!jws.getPayload().getAudience().contains(properties.audience())) {
            throw new JwtException("Token emitido para outro destinatário");
        }
        return jws;
    }
}
