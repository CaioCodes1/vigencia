package com.caiocodes.crbap.shared.domain.exception;

/**
 * Base de todo erro de domínio.
 *
 * <p>Todas são {@code unchecked} de propósito: quebra de regra de negócio não é
 * algo que o chamador intermediário deva tratar linha a linha — quem traduz
 * para HTTP é o {@code GlobalExceptionHandler}, num lugar só.
 *
 * <p>O {@code code} é estável e vai para a resposta da API. A mensagem pode
 * mudar de redação sem quebrar cliente; o código, não.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
