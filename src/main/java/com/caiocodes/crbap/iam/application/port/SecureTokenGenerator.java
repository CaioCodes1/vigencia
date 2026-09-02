package com.caiocodes.crbap.iam.application.port;

/** Gera o refresh token: string aleatória e imprevisível. */
public interface SecureTokenGenerator {

    String generate();
}
