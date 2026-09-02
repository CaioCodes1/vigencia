package com.caiocodes.crbap.iam.application.port;

/**
 * Porta de hash de senha. A implementação (Argon2id) mora na infraestrutura —
 * o caso de uso não sabe qual algoritmo é, e trocar de algoritmo não toca em
 * regra de negócio.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);

    /**
     * Gasta o mesmo tempo de uma verificação real, sem verificar nada.
     *
     * <p>Existe por causa de um vazamento sutil: se o login responde em 2 ms
     * quando o e-mail não existe e em 400 ms quando existe (porque só neste
     * caso o Argon2 roda), dá para descobrir quem tem conta apenas cronometrando
     * as respostas. Chamar isto no caminho "usuário não encontrado" iguala os
     * dois tempos.
     */
    void simulateVerification();
}
