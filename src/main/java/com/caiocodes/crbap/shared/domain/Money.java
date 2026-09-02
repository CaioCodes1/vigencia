package com.caiocodes.crbap.shared.domain;

import com.caiocodes.crbap.shared.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Valor monetário: quantia + moeda, imutável e validado na construção.
 *
 * <p>Três bugs que este tipo elimina de uma vez:
 * <ul>
 *   <li>{@code double} para dinheiro — {@code 0.1 + 0.2} dá
 *       {@code 0.30000000000000004}, e em 12 parcelas isso vira centavo de
 *       diferença e conciliação bancária quebrada;</li>
 *   <li>somar reais com dólares — estoura na hora, em vez de gerar um número
 *       sem significado;</li>
 *   <li>comparar com {@code equals} —
 *       {@code new BigDecimal("10.0").equals(new BigDecimal("10.00"))} é
 *       {@code false}, porque a escala faz parte da igualdade. Aqui a escala é
 *       normalizada para 2 na construção, então {@code equals} passa a ser
 *       seguro.</li>
 * </ul>
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    private static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount é obrigatório");
        Objects.requireNonNull(currency, "currency é obrigatório");
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "Valor monetário com mais de 2 casas decimais: " + amount);
        }
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "o outro valor é obrigatório");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency.getCurrencyCode(),
                    other.currency.getCurrencyCode());
        }
    }
}
