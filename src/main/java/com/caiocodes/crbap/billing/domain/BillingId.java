package com.caiocodes.crbap.billing.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de cobrança. */
public record BillingId(UUID value) {

    public BillingId {
        Objects.requireNonNull(value, "o id da cobrança é obrigatório");
    }

    public static BillingId newId() {
        return new BillingId(UUID.randomUUID());
    }

    public static BillingId of(UUID value) {
        return new BillingId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
