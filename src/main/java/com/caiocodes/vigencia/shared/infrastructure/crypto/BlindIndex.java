package com.caiocodes.vigencia.shared.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Índice cego: HMAC-SHA256 do valor, para buscar e garantir unicidade sem
 * decifrar nada.
 *
 * <p>O problema que ele resolve: cifrar o CPF protege o dado, mas destrói a
 * busca — cada gravação usa um IV diferente, então o mesmo CPF vira bytes
 * diferentes no banco e nenhum {@code WHERE} funciona. A saída é guardar, ao
 * lado do valor cifrado, um segundo valor <b>determinístico</b> e não
 * reversível.
 *
 * <p><b>Por que HMAC e não SHA-256 puro:</b> só existem cerca de 10¹¹ CPFs. Uma
 * tabela com o hash de todos eles se calcula em minutos num notebook — o hash
 * puro não esconderia nada. O HMAC depende de uma chave secreta: sem ela, não
 * há força bruta possível.
 */
@Component
@RequiredArgsConstructor
public class BlindIndex {

    private static final String ALGORITHM = "HmacSHA256";

    private final CryptoKeys keys;

    /**
     * Normaliza antes de calcular: "123.456.789-09" e "12345678909" precisam
     * gerar o mesmo índice, senão a unicidade não vale nada.
     */
    public byte[] of(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keys.indexKey());
            return mac.doFinal(normalize(value).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular o índice cego", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s", "").toLowerCase();
    }
}
