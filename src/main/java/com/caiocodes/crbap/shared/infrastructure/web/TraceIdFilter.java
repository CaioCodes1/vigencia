package com.caiocodes.crbap.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dá um identificador a cada requisição e o coloca no MDC — o "porta-luvas" do
 * log da thread. Tudo que for logado daqui para frente sai com esse traceId, o
 * que permite colar um id no Grafana e ver a requisição inteira, inclusive o
 * consumidor da fila três segundos depois.
 *
 * <p>Ordem 1: precisa rodar antes de qualquer coisa que possa logar.
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = resolve(request.getHeader(TRACE_ID_HEADER));
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Obrigatório: sem isto, a thread volta ao pool carregando o
            // traceId da requisição anterior e o log passa a mentir.
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * Aceita o id vindo do cliente para correlacionar chamadas entre sistemas,
     * mas com tamanho limitado e só com caracteres seguros — header é entrada
     * de usuário, e isso vai parar no log.
     */
    private String resolve(String incoming) {
        if (incoming == null || incoming.isBlank() || incoming.length() > 64
                || !incoming.matches("[A-Za-z0-9_-]+")) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }
}
