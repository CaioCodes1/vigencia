package com.caiocodes.vigencia.iam.domain.exception;

import com.caiocodes.vigencia.shared.domain.exception.DomainException;

/**
 * Refresh token inexistente, expirado, revogado — ou reapresentado depois de já
 * ter sido usado, que é o sinal de roubo. Vira HTTP 401.
 *
 * <p>Também aqui a mensagem é única para todos os casos: dizer "este token já
 * foi usado" contaria ao atacante que ele chegou tarde, e dizer "expirado"
 * contaria que o token era válido um dia.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "Sessão inválida ou expirada. Faça login novamente.");
    }
}
