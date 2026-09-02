package com.caiocodes.crbap.iam.infrastructure.security;

import com.caiocodes.crbap.shared.infrastructure.web.ApiError;
import com.caiocodes.crbap.shared.infrastructure.web.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita tentativas de login por IP.
 *
 * <p>Contador de janela fixa com {@code INCR} + {@code EXPIRE} no Redis — duas
 * operações atômicas, e o estado é compartilhado entre todas as instâncias. Com
 * um contador em memória e três réplicas, o limite efetivo seria três vezes o
 * configurado, e o atacante nem precisaria saber disso.
 *
 * <p><b>Trade-off assumido:</b> janela fixa permite uma rajada na virada da
 * janela (5 no último segundo do minuto + 5 no primeiro do minuto seguinte). Para
 * força bruta de senha isso é irrelevante — o que importa é o teto por hora, e o
 * bloqueio de conta do {@code LoginUseCase} cobre a mesma superfície por outro
 * ângulo. Se um dia precisar de suavização real, entra token bucket (Bucket4j).
 *
 * <p>Este filtro é a exceção à regra de "filtro não escreve resposta de erro":
 * ele precisa cortar a requisição antes de tudo. O formato continua único,
 * porque ele serializa o mesmo {@link ApiError} do resto da API.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS =
            Set.of("/api/v1/auth/login", "/api/v1/auth/refresh");
    private static final String KEY_PREFIX = "ratelimit:auth:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final JwtProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = KEY_PREFIX + request.getRequestURI() + ":" + clientIp(request);
        long attempts;
        try {
            Long current = redis.opsForValue().increment(key);
            attempts = current == null ? 1L : current;
            if (attempts == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (RuntimeException e) {
            // Falha aberta aqui, de propósito: Redis fora do ar não pode
            // impedir o login de todo mundo. Mas gera ERROR para alguém olhar.
            log.error("Redis indisponível no rate limit — liberando a requisição", e);
            chain.doFilter(request, response);
            return;
        }

        if (attempts > properties.loginAttemptsPerMinute()) {
            log.warn("auth.rate_limit.excedido rota={} ip={} tentativas={}",
                    request.getRequestURI(), clientIp(request), attempts);
            writeTooManyRequests(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW.toSeconds()));
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "Muitas tentativas. Tente novamente em " + WINDOW.toSeconds() + " segundos.",
                request.getRequestURI(),
                TraceIdFilter.currentTraceId()));
    }

    /**
     * X-Forwarded-For só é aceito de um proxy confiável. Confiar nele sempre
     * seria entregar o rate limit de bandeja: qualquer cliente forjaria o header
     * e teria um balde novo a cada requisição.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && isTrustedProxy(request.getRemoteAddr())) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null
                && (remoteAddr.startsWith("10.") || remoteAddr.startsWith("172.")
                    || remoteAddr.startsWith("192.168.") || remoteAddr.startsWith("127."));
    }
}
