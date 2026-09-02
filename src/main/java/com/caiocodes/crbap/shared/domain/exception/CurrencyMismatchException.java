package com.caiocodes.crbap.shared.domain.exception;

/** Operação entre valores de moedas diferentes. Vira HTTP 422. */
public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(String expected, String actual) {
        super("CURRENCY_MISMATCH",
                "Moedas diferentes na mesma operação: " + expected + " e " + actual);
    }
}
