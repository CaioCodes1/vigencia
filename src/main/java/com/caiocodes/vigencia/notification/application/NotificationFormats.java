package com.caiocodes.vigencia.notification.application;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/**
 * Formatação do que vai no e-mail.
 *
 * <p><b>Formata aqui, no Java, e não no template.</b> Dois motivos: isto é
 * testável num teste unitário de milissegundos, e o texto formatado fica
 * gravado no payload JSONB — então "que aviso vocês me mandaram?" tem resposta
 * exata meses depois, mesmo que o template tenha mudado no meio do caminho.
 */
public final class NotificationFormats {

    private static final Locale BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private NotificationFormats() {
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DATA);
    }

    /** {@code R$ 24.000,00} — com o símbolo, porque o e-mail vai para humano. */
    public static String money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(BR);
        formato.setCurrency(Currency.getInstance(
                currencyCode == null || currencyCode.isBlank() ? "BRL" : currencyCode));
        // O espaço não-quebrável que o JDK usa entre símbolo e número aparece
        // como "?" em cliente de e-mail antigo. Troca por espaço comum.
        return formato.format(amount).replace('\u00A0', ' ');
    }
}
