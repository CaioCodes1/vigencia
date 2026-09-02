package com.caiocodes.crbap.iam.domain.exception;

import com.caiocodes.crbap.shared.domain.exception.DomainException;

/**
 * Credencial inválida. Vira HTTP 401.
 *
 * <p>A mensagem é <b>deliberadamente genérica e sempre a mesma</b>, tanto para
 * e-mail inexistente quanto para senha errada. Diferenciar as duas transforma a
 * API num verificador de e-mails cadastrados: o atacante descobre quem tem conta
 * antes de tentar qualquer senha.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Credenciais inválidas");
    }
}
