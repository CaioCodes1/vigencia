package com.caiocodes.crbap.reporting.application;

import java.math.BigDecimal;

/**
 * Uma faixa do gráfico de vencimentos: {@code "0-7"}, {@code "8-15"}…
 *
 * <p>As faixas são as mesmas da régua de avisos da fase 6, de propósito. Painel
 * e notificação dividindo o mesmo corte deixam a conversa possível: "os sete
 * que aparecem em vermelho são os que já receberam o segundo aviso".
 */
public record ExpiringBucket(String range, long contracts, BigDecimal totalValue) {
}
