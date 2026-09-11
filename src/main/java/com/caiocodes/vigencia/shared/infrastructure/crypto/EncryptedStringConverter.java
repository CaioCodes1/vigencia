package com.caiocodes.vigencia.shared.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cifra um campo de texto em repouso, com AES-256-GCM.
 *
 * <p><b>GCM e não CBC</b> porque GCM é autenticado: adulterar o ciphertext no
 * banco é detectado na decifragem, em vez de produzir texto lixo que o sistema
 * aceitaria como válido.
 *
 * <p><b>IV novo a cada gravação</b>, guardado junto do ciphertext. Reusar IV em
 * GCM quebra o esquema inteiro — é o erro clássico dessa cifra, e por isso o IV
 * nunca é constante nem derivado do dado.
 *
 * <p>É um {@code @Component} de propósito: o Spring Boot registra o
 * {@code SpringBeanContainer} no Hibernate, então o conversor é resolvido como
 * bean e recebe as chaves por construtor. Sem isso, o Hibernate o instanciaria
 * com o construtor vazio e não haveria como injetar a chave.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final CryptoKeys keys;
    private final SecureRandom random = new SecureRandom();

    @Override
    public byte[] convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.dataKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);
            return result;
        } catch (Exception e) {
            // Sem o valor no log: a mensagem de erro vai para o Loki e o backup.
            throw new IllegalStateException("Falha ao cifrar campo sensível", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] iv = Arrays.copyOfRange(stored, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(stored, IV_LENGTH, stored.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keys.dataKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Falha ao decifrar campo sensível — chave trocada ou dado adulterado", e);
        }
    }
}
