package com.caiocodes.vigencia.iam.application;

import java.time.Instant;

/**
 * @param accessTokenId        o {@code jti} do token que fez a chamada
 * @param accessTokenExpiresAt até quando ele precisa ficar na denylist
 */
public record LogoutCommand(
        String refreshToken,
        String accessTokenId,
        Instant accessTokenExpiresAt) {
}
