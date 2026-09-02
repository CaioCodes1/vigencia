package com.caiocodes.crbap.shared.infrastructure.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * As duas chaves usadas para proteger dados sensíveis em repouso.
 *
 * <p><b>Por que duas chaves separadas:</b> a de cifra (AES) e a do índice cego
 * (HMAC) têm propósitos diferentes, e usar a mesma para as duas coisas é um
 * erro clássico — o vazamento de uma comprometeria as duas funções de uma vez.
 *
 * <p>Mesma política do {@code JwtKeyProvider}: sem as variáveis de ambiente,
 * gera chaves efêmeras fora de produção e <b>falha na subida</b> no perfil
 * {@code prod}. A diferença é que aqui o custo de uma chave efêmera é maior —
 * o que foi gravado antes de reiniciar deixa de ser legível — e por isso o
 * aviso é mais duro.
 */
@Slf4j
@Component
public class CryptoKeys {

    private static final int KEY_BYTES = 32;

    private final SecretKey dataKey;
    private final SecretKey indexKey;

    public CryptoKeys(@Value("${crbap.crypto.data-key:}") String dataKeyBase64,
                      @Value("${crbap.crypto.index-key:}") String indexKeyBase64,
                      Environment environment) {
        boolean production = environment.matchesProfiles("prod");
        this.dataKey = load(dataKeyBase64, "DATA_ENCRYPTION_KEY", "AES", production);
        this.indexKey = load(indexKeyBase64, "BLIND_INDEX_KEY", "HmacSHA256", production);
    }

    public SecretKey dataKey() {
        return dataKey;
    }

    public SecretKey indexKey() {
        return indexKey;
    }

    private SecretKey load(String base64, String variable, String algorithm, boolean production) {
        if (base64 == null || base64.isBlank()) {
            if (production) {
                throw new IllegalStateException(variable + " é obrigatória no perfil prod");
            }
            log.warn("{} ausente — chave EFÊMERA gerada. Todo dado cifrado agora fica "
                    + "ilegível na próxima reinicialização. Só use assim em dev.", variable);
            byte[] random = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, algorithm);
        }
        byte[] key = Base64.getDecoder().decode(base64.strip());
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(variable + " deve ter 32 bytes em base64 "
                    + "(gere com: openssl rand -base64 32) — recebidos " + key.length);
        }
        return new SecretKeySpec(key, algorithm);
    }
}
