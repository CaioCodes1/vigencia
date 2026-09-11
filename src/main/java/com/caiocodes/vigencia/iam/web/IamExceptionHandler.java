package com.caiocodes.vigencia.iam.web;

import com.caiocodes.vigencia.iam.domain.exception.AccountLockedException;
import com.caiocodes.vigencia.iam.domain.exception.InvalidCredentialsException;
import com.caiocodes.vigencia.iam.domain.exception.InvalidRefreshTokenException;
import com.caiocodes.vigencia.shared.infrastructure.web.ApiError;
import com.caiocodes.vigencia.shared.infrastructure.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tradução HTTP das exceções de autenticação.
 *
 * <p>Fica no módulo, e não no {@code GlobalExceptionHandler} compartilhado, por
 * uma razão de fronteira: o pacote {@code shared} não deve conhecer as exceções
 * de {@code iam}. Cada módulo é dono da própria tradução; o handler global
 * cuida só do que é de todos.
 */
@RestControllerAdvice
public class IamExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> onInvalidCredentials(InvalidCredentialsException e,
                                                         HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.code(), e.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> onInvalidRefreshToken(InvalidRefreshTokenException e,
                                                          HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.code(), e.getMessage(), request);
    }

    /** 423 Locked: a conta existe e está temporariamente trancada. */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> onAccountLocked(AccountLockedException e,
                                                    HttpServletRequest request) {
        return build(HttpStatus.LOCKED, e.code(), e.getMessage(), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message,
                request.getRequestURI(), TraceIdFilter.currentTraceId()));
    }
}
