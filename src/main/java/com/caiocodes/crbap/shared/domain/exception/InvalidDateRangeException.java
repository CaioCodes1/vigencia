package com.caiocodes.crbap.shared.domain.exception;

import java.time.LocalDate;

/** Período com fim antes (ou igual) ao início. Vira HTTP 422. */
public class InvalidDateRangeException extends DomainException {

    public InvalidDateRangeException(LocalDate start, LocalDate end) {
        super("INVALID_DATE_RANGE",
                "A data de fim (" + end + ") deve ser posterior à de início (" + start + ")");
    }
}
