package com.caiocodes.vigencia.iam.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de papel. */
public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "o id do papel é obrigatório");
    }

    public static RoleId newId() {
        return new RoleId(UUID.randomUUID());
    }

    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
