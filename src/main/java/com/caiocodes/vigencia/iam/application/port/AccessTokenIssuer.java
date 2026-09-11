package com.caiocodes.vigencia.iam.application.port;

import com.caiocodes.vigencia.iam.domain.User;
import java.time.Instant;

/** Porta de emissão do access token. A implementação JWT fica na infra. */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user, Instant now);

    /**
     * @param value      o token serializado
     * @param jti        identificador único, usado para revogar no logout
     * @param expiresAt  quando deixa de valer
     */
    record IssuedAccessToken(String value, String jti, Instant expiresAt) {
    }
}
