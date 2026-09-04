package com.caiocodes.crbap.billing.domain;

/**
 * Situação da cobrança.
 *
 * <p>{@code OVERDUE} é o único estado aqui que depende do relógio, e por isso é
 * o único que um job precisa escrever. Não dá para derivá-lo como se faz com
 * "contrato vencendo": a inadimplência dispara notificação e entra em relatório,
 * então precisa de um instante registrado em que passou a valer.
 */
public enum BillingStatus {

    /** Emitida, aguardando pagamento. */
    PENDING,

    /** Recebeu pagamento, mas ainda há saldo. */
    PARTIALLY_PAID,

    /** Quitada. Estado final — até que alguém estorne. */
    PAID,

    /** Passou do vencimento sem quitação. */
    OVERDUE,

    /** Cancelada com motivo. Estado final e não recebe pagamento. */
    CANCELLED;

    /** Ainda pode receber dinheiro. */
    public boolean isOpen() {
        return this == PENDING || this == PARTIALLY_PAID || this == OVERDUE;
    }
}
