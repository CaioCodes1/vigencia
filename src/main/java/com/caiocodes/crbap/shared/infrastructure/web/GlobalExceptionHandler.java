package com.caiocodes.crbap.shared.infrastructure.web;

import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import com.caiocodes.crbap.shared.domain.exception.CurrencyMismatchException;
import com.caiocodes.crbap.shared.domain.exception.DomainException;
import com.caiocodes.crbap.shared.domain.exception.IllegalStateTransitionException;
import com.caiocodes.crbap.shared.domain.exception.InvalidDateRangeException;
import com.caiocodes.crbap.shared.domain.exception.InvalidDocumentException;
import com.caiocodes.crbap.shared.domain.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz exceção em resposta HTTP num lugar só.
 *
 * <p>É isto que permite o controller não ter {@code try/catch} e o domínio
 * lançar exceção sem saber que HTTP existe.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> onBusinessRule(BusinessRuleException e,
                                                   HttpServletRequest req) {
        log.info("Regra de negócio violada em {}: {}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, e.code(), e.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiError> onTransition(IllegalStateTransitionException e,
                                                 HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, e.code(), e.getMessage(), req);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(NotFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, e.code(), e.getMessage(), req);
    }

    @ExceptionHandler({InvalidDocumentException.class, InvalidDateRangeException.class,
            CurrencyMismatchException.class})
    public ResponseEntity<ApiError> onSemanticError(DomainException e, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.code(), e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e,
                                                 HttpServletRequest req) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(
                ApiError.validation(req.getRequestURI(), TraceIdFilter.currentTraceId(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e,
                                                 HttpServletRequest req) {
        // A mensagem original expõe nome de classe Java; o cliente recebe só o essencial.
        log.debug("Corpo ilegível em {}: {}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Corpo da requisição ausente ou mal formado", req);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> onBadParameter(Exception e, HttpServletRequest req) {
        log.debug("Parâmetro inválido em {}: {}", req.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parâmetro ausente ou com tipo inválido", req);
    }

    /**
     * Cabeçalho obrigatório ausente — hoje só o {@code Idempotency-Key} da
     * renovação.
     *
     * <p>Sem este handler a exceção cairia na rede de segurança lá embaixo e
     * viraria <b>500</b>: quem esqueceu um cabeçalho receberia "erro interno" e
     * abriria chamado, quando o problema é dele e o 400 já diz qual.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> onMissingHeader(MissingRequestHeaderException e,
                                                    HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Cabeçalho obrigatório ausente: " + e.getHeaderName(), req);
    }

    /**
     * Sem este handler, o {@code AccessDeniedException} lançado pelo
     * {@code @PreAuthorize} cai no handler genérico de {@code Exception} logo
     * abaixo e vira <b>500</b>.
     *
     * <p>O motivo é a ordem das coisas: o {@code AccessDeniedHandler} do Spring
     * Security vive no {@code ExceptionTranslationFilter}, que está <i>acima</i>
     * do DispatcherServlet — mas o {@code @RestControllerAdvice} intercepta a
     * exceção antes de ela chegar lá. Resultado: um 403 legítimo era reportado
     * como erro interno, e o cliente não tinha como distinguir "sem permissão"
     * de "o servidor quebrou".
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException e,
                                                   HttpServletRequest req) {
        // Sem dizer qual permissão faltou: a lista de permissões do sistema é
        // informação útil para quem está sondando a API.
        log.info("Acesso negado em {}", req.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Você não tem permissão para executar esta operação", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> onUnknownRoute(NoResourceFoundException e,
                                                   HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado", req);
    }

    /**
     * Rede de segurança. O usuário recebe uma referência; a stack trace fica no
     * log, correlacionada pelo mesmo traceId. Devolver stack trace na resposta
     * entrega versão de framework e nome de tabela de graça (OWASP A05).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e, HttpServletRequest req) {
        String reference = UUID.randomUUID().toString();
        log.error("Erro inesperado ref={} em {}", reference, req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno. Informe a referência " + reference, req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message,
                req.getRequestURI(), TraceIdFilter.currentTraceId()));
    }
}
