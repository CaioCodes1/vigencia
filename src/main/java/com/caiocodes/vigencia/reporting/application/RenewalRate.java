package com.caiocodes.vigencia.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A taxa de renovação de um período — o número que diz se o negócio se sustenta.
 *
 * <p>O denominador são os contratos que <b>chegaram ao fim</b> no período
 * (renovados, expirados ou cancelados), nunca a base inteira: incluir contratos
 * que ainda estão correndo diluiria a taxa até ela não significar nada.
 */
public record RenewalRate(String period, long renewed, long lost, BigDecimal ratePercent) {

    public static RenewalRate of(String period, long renewed, long lost) {
        long total = renewed + lost;
        // Período sem contrato encerrado devolve zero, não divisão por zero.
        // É o primeiro mês de operação — e ele existe em toda implantação.
        BigDecimal taxa = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(renewed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new RenewalRate(period, renewed, lost, taxa);
    }
}
