package com.caiocodes.vigencia.contract.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de contrato. */
public record ContractId(UUID value) {

    public ContractId {
        Objects.requireNonNull(value, "o id do contrato é obrigatório");
    }

    public static ContractId newId() {
        return new ContractId(UUID.randomUUID());
    }

    public static ContractId of(UUID value) {
        return new ContractId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
