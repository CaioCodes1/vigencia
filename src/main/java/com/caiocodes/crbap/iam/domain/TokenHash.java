package com.caiocodes.crbap.iam.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 do refresh token, em hexadecimal.
 *
 * <p>O banco guarda só isto: um dump da tabela {@code refresh_tokens} não dá
 * sessão a ninguém. E aqui, ao contrário do hash de senha, <b>rápido é bom</b> —
 * o token é uma string aleatória de 256 bits, não uma senha escolhida por
 * humano, então não existe dicionário para atacar e não faz sentido pagar o
 * custo do Argon2 a cada renovação.
 */
public final class TokenHash {

    private TokenHash() {
    }

    public static String of(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
