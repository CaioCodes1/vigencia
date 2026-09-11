package com.caiocodes.vigencia.shared.infrastructure.web;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de erro da API, no espírito do RFC 7807 (Problem Details).
 *
 * <p>Um formato só para todos os erros é o que permite ao cliente tratar falha
 * de forma genérica. O {@code code} é estável — a {@code message} pode mudar de
 * redação. E nunca há stack trace aqui: ela entrega versão de framework, nome de
 * tabela e caminho de arquivo (OWASP A05). O time acha o erro pelo traceId.
 *
 * @param fieldErrors preenchido só em erro de validação
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, code, message, path, traceId, null);
    }

    public static ApiError validation(String path, String traceId, List<FieldError> fields) {
        return new ApiError(Instant.now(), 400, "VALIDATION_ERROR",
                "Requisição inválida: verifique os campos informados", path, traceId, fields);
    }
}
