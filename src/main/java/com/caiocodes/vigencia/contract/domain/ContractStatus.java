package com.caiocodes.vigencia.contract.domain;

/**
 * Situação do contrato.
 *
 * <p>Repare no que <b>não</b> está aqui: {@code EXPIRING_SOON}. "Vence em 30
 * dias" é derivado de {@code end_date} e da data de hoje — guardar isso criaria
 * um estado que fica errado sozinho no dia em que o job não rodar. A regra
 * geral: guarde fatos, calcule opiniões.
 */
public enum ContractStatus {

    /** Em elaboração. Ainda pode ser editado e não gera cobrança. */
    DRAFT,

    /** Vigente. É o único estado que gera cobrança e dispara aviso de vencimento. */
    ACTIVE,

    /** Suspenso temporariamente (inadimplência, negociação). Volta para ACTIVE. */
    SUSPENDED,

    /** Encerrado porque um sucessor assumiu. Estado final. */
    RENEWED,

    /** Passou da data de fim sem renovação. Ainda pode virar RENEWED por 30 dias. */
    EXPIRED,

    /** Encerrado por decisão, com motivo obrigatório. Estado final. */
    CANCELLED;

    public boolean isFinal() {
        return this == RENEWED || this == CANCELLED;
    }
}
