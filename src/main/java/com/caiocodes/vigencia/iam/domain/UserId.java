package com.caiocodes.vigencia.iam.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de usuário.
 *
 * <p>Um {@code record} em volta do UUID em vez do UUID cru: o compilador passa a
 * recusar {@code findUser(contractId)}. Custa uma classe e elimina uma classe
 * inteira de bug — trocar um id por outro compila perfeitamente quando tudo é
 * {@code UUID}.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "o id do usuário é obrigatório");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
