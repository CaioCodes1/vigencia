package com.caiocodes.vigencia.notification.domain;

/** Situação do envio. */
public enum NotificationStatus {

    /** Agendada, esperando o job de envio. */
    PENDING,

    /** Saiu. Estado final. */
    SENT,

    /**
     * Desistiu depois de esgotar as tentativas, ou o erro é permanente
     * (e-mail inválido). Fica <b>fora</b> do índice único, para permitir o
     * reenvio manual.
     */
    FAILED,

    /** Deixou de fazer sentido antes de sair — o contrato foi renovado, por exemplo. */
    CANCELLED
}
