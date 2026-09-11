package com.caiocodes.vigencia.shared.domain.exception;

/** Regra de negócio violada. Vira HTTP 409. */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
