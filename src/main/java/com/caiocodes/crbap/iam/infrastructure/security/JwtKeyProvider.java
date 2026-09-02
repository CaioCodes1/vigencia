package com.caiocodes.crbap.iam.infrastructure.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fornece o par de chaves RSA que assina e verifica o access token.
 *
 * <p><b>Por que RS256 e não HS256:</b> com HS256 (chave simétrica), todo serviço
 * que <i>valida</i> um token precisa da mesma chave que o <i>assina</i> — logo,
 * qualquer um deles pode forjar tokens. Com RS256, distribui-se apenas a chave
 * pública.
 *
 * <p>Se as chaves não vierem do ambiente, um par efêmero é gerado na subida.
 * Isso é uma conveniência de desenvolvimento e de teste: em produção o processo
 * <b>falha na subida</b>, porque um par efêmero invalidaria todas as sessões a
 * cada deploy e quebraria qualquer instalação com mais de uma instância.
 */
@Slf4j
@Component
public class JwtKeyProvider {

    private static final int KEY_SIZE = 2048;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtKeyProvider(JwtProperties properties, Environment environment) {
        boolean production = environment.matchesProfiles("prod");
        if (isBlank(properties.privateKey()) || isBlank(properties.publicKey())) {
            if (production) {
                throw new IllegalStateException(
                        "JWT_PRIVATE_KEY e JWT_PUBLIC_KEY são obrigatórias no perfil prod");
            }
            KeyPair pair = generateEphemeralPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
            log.warn("Par de chaves RSA EFÊMERO gerado — válido só para dev/teste. "
                    + "Toda reinicialização invalida os tokens emitidos.");
        } else {
            this.privateKey = parsePrivateKey(properties.privateKey());
            this.publicKey = parsePublicKey(properties.publicKey());
        }
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    private static KeyPair generateEphemeralPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o par RSA", e);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY inválida (esperado PKCS#8 PEM)", e);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PUBLIC_KEY inválida (esperado X.509 PEM)", e);
        }
    }

    /** Aceita o PEM com quebras de linha reais ou escapadas como {@code \n}. */
    private static byte[] decodePem(String pem, String marker) {
        String body = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN " + marker + "-----", "")
                .replace("-----END " + marker + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
