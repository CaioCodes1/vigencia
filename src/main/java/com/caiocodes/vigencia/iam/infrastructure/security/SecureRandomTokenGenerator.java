package com.caiocodes.vigencia.iam.infrastructure.security;

import com.caiocodes.vigencia.iam.application.port.SecureTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Refresh token: 256 bits de {@link SecureRandom}, em Base64 sem padding.
 *
 * <p>{@code SecureRandom}, e não {@code Random} nem {@code UUID.randomUUID()}
 * como fonte de segredo: o primeiro é previsível a partir de algumas amostras, e
 * o UUID v4 tem 122 bits de entropia com formato conhecido. Aqui o token é uma
 * senha de sessão — precisa ser imprevisível de verdade.
 */
@Component
public class SecureRandomTokenGenerator implements SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
