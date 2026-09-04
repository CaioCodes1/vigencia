package com.caiocodes.crbap.notification.domain;

/**
 * O que está sendo avisado.
 *
 * <p>O tipo faz parte da chave de unicidade junto com a janela
 * ({@code days_offset}) e o destinatário: é a combinação dos três que responde
 * "este aviso já foi mandado?".
 */
public enum NotificationType {

    /** Contrato vencendo — a janela diz se é D-30, D-15, D-7 ou D-1. */
    CONTRACT_EXPIRING,

    /** Contrato que passou da data sem renovação. */
    CONTRACT_EXPIRED,

    /** Renovação confirmada, com o novo período e o novo valor. */
    CONTRACT_RENEWED,

    /** Cobrança emitida. */
    BILLING_CREATED,

    /** Cobrança vencendo em poucos dias. */
    BILLING_DUE_SOON,

    /** Cobrança vencida — a janela diz há quantos dias. */
    BILLING_OVERDUE,

    /** Recibo de pagamento. */
    PAYMENT_RECEIVED
}
