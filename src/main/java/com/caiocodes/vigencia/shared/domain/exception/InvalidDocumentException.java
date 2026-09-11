package com.caiocodes.vigencia.shared.domain.exception;

/**
 * CPF ou CNPJ inválido. Vira HTTP 422.
 *
 * <p>A mensagem nunca inclui o documento inteiro — só o tamanho — porque
 * mensagem de erro vai para log, e log vai para backup.
 */
public class InvalidDocumentException extends DomainException {

    public InvalidDocumentException(String type, String digits) {
        super("INVALID_DOCUMENT",
                "Documento inválido para o tipo " + type
                        + " (" + (digits == null ? 0 : digits.length()) + " dígitos)");
    }
}
