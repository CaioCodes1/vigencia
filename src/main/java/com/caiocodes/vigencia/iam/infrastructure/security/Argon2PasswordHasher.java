package com.caiocodes.vigencia.iam.infrastructure.security;

import com.caiocodes.vigencia.iam.application.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hash de senha com Argon2id, nos parâmetros recomendados pelo OWASP (2024):
 * {@code m=19456 KiB, t=2, p=1}.
 *
 * <p><b>O parâmetro que importa é a memória.</b> Uma GPU tem milhares de núcleos
 * e pouca memória por núcleo; exigir 19 MiB por tentativa anula a vantagem dela
 * e derruba um quebra-senhas de bilhões para milhares de tentativas por segundo.
 * É por isso que SHA-256 não serve aqui: ele é <i>rápido</i>, e velocidade é
 * exatamente o que não se quer.
 *
 * <p><b>Calibre na máquina de produção:</b> o hash deve levar entre 300 ms e 1 s.
 * Rápido demais é fraco; lento demais transforma o próprio login em DoS.
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
            SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);

    /**
     * Hash de uma senha fictícia, calculado uma vez na subida. Serve para o
     * caminho "usuário não existe" gastar o mesmo tempo do caminho normal.
     */
    private final String dummyHash = encoder.encode("senha-que-nao-existe-em-lugar-nenhum");

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }

    @Override
    public void simulateVerification() {
        encoder.matches("verificacao-fantasma", dummyHash);
    }
}
