package com.caiocodes.vigencia.client.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de cliente. */
public record ClientId(UUID value) {

    public ClientId {
        Objects.requireNonNull(value, "o id do cliente é obrigatório");
    }

    public static ClientId newId() {
        return new ClientId(UUID.randomUUID());
    }

    public static ClientId of(UUID value) {
        return new ClientId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
