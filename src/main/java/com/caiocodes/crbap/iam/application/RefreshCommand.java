package com.caiocodes.crbap.iam.application;

/** Entrada da renovação de sessão. */
public record RefreshCommand(String refreshToken, String userAgent, String ipAddress) {
}
