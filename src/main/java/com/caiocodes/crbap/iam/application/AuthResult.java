package com.caiocodes.crbap.iam.application;

/**
 * Resultado de um login ou de uma renovação.
 *
 * @param refreshToken valor <b>cru</b>, a única vez em que ele existe fora do
 *                     cliente: o banco guarda apenas o SHA-256
 */
public record AuthResult(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserProfile user) {
}
