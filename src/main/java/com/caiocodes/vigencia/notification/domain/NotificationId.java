package com.caiocodes.vigencia.notification.domain;

import java.util.Objects;
import java.util.UUID;

/** Identificador de notificação. */
public record NotificationId(UUID value) {

    public NotificationId {
        Objects.requireNonNull(value, "o id da notificação é obrigatório");
    }

    public static NotificationId newId() {
        return new NotificationId(UUID.randomUUID());
    }

    public static NotificationId of(UUID value) {
        return new NotificationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
