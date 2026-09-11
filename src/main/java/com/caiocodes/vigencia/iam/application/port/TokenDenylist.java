package com.caiocodes.vigencia.iam.application.port;

import java.time.Instant;

/**
 * Lista de access tokens revogados antes da hora (logout).
 *
 * <p>JWT é, por natureza, válido até expirar — não existe "apagar" um token já
 * emitido. O logout, então, guarda o {@code jti} numa lista consultada a cada
 * requisição, com TTL igual ao tempo que falta para o token expirar: depois
 * disso a entrada some sozinha, porque o token já não vale por si. A lista
 * nunca cresce sem limite, e isso é de propósito.
 */
public interface TokenDenylist {

    void revoke(String jti, Instant expiresAt);

    boolean isRevoked(String jti);
}
