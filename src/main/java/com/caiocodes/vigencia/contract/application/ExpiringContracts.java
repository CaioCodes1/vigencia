package com.caiocodes.vigencia.contract.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A resposta de {@code GET /contracts/expiring}: a tela que substitui a
 * planilha.
 *
 * <p>As faixas ({@code 0-7}, {@code 8-15}, {@code 16-30}) são calculadas a
 * partir de {@code referenceDate}, nunca guardadas. É o mesmo princípio do
 * status não ter {@code EXPIRING_SOON}: se a faixa estivesse numa coluna,
 * ficaria errada todo dia à meia-noite.
 */
public record ExpiringContracts(
        LocalDate referenceDate,
        int daysAhead,
        Summary summary,
        List<ContractSummary> content) {

    /**
     * @param buckets faixa → quantidade, ex.: {@code {"0-7": 3, "8-15": 5}}
     */
    public record Summary(Map<String, Long> buckets, long total, BigDecimal totalValue,
                          String currency) {
    }
}
