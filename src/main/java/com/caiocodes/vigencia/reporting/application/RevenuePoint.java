package com.caiocodes.vigencia.reporting.application;

import java.math.BigDecimal;

/**
 * Um mês da série de receita: o que entrou e o que era esperado.
 *
 * <p>{@code month} vem como {@code "2026-09"} — string, e não data. O ponto é
 * um mês inteiro, e devolver {@code 2026-09-01} convidaria o gráfico a tratar
 * como dia e a errar o rótulo no fuso do navegador.
 */
public record RevenuePoint(String month, BigDecimal realized, BigDecimal expected) {
}
