package com.caiocodes.vigencia.audit.infrastructure;

import com.caiocodes.vigencia.audit.application.AuditContext;
import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.audit.application.RecordAuditUseCase;
import com.caiocodes.vigencia.audit.application.RecordAuditUseCase.AuditRequest;
import com.caiocodes.vigencia.shared.infrastructure.web.TraceIdFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Transforma {@link Auditable} numa linha da trilha.
 *
 * <p><b>A ordem é o detalhe que faz isto funcionar.</b>
 * {@code LOWEST_PRECEDENCE - 1} coloca este aspecto <i>por fora</i> do
 * interceptador de transação (que fica em {@code LOWEST_PRECEDENCE}). Então
 * {@code proceed()} só devolve depois do commit, e a auditoria registra o que
 * de fato aconteceu — não o que se tentou fazer.
 *
 * <p>Por dentro da transação seria pior de um jeito discreto: uma ação que der
 * rollback depois deixaria a linha de auditoria dizendo que o contrato foi
 * renovado. Auditoria que mente é pior que auditoria que falta.
 *
 * <p><b>Falha de auditoria não derruba o negócio.</b> A ação já commitou; jogar
 * exceção agora devolveria erro ao cliente por uma renovação que aconteceu, e o
 * operador tentaria de novo. Fica no log em {@code ERROR} — que na fase 8 vira
 * alerta.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class AuditAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETROS = new DefaultParameterNameDiscoverer();
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };

    /**
     * Campos que nunca entram na trilha, em qualquer nível do JSON.
     *
     * <p>Auditoria é lida por muito mais gente do que a tabela original — é o
     * lugar mais fácil de vazar o que a criptografia de campo do módulo de
     * clientes protege. Documento e senha ficam de fora; o id da entidade já
     * responde "de quem estamos falando".
     */
    private static final Set<String> REDIGIDOS = Set.of(
            "password", "currentPassword", "newPassword", "passwordHash",
            "document", "documentIndex", "token", "accessToken", "refreshToken");

    private static final String MASCARA = "***";

    private final RecordAuditUseCase recorder;
    private final ClientIpResolver ips;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditable)")
    public Object auditar(ProceedingJoinPoint ponto, Auditable auditable) throws Throwable {
        Object resultado;
        try {
            resultado = ponto.proceed();
        } catch (Throwable erro) {
            // A foto do "antes" morre com a tentativa que falhou: deixá-la na
            // thread contaminaria a próxima requisição a pegar esta thread.
            AuditContext.clear();
            throw erro;
        }

        try {
            registrar(ponto, auditable, resultado);
        } catch (RuntimeException e) {
            log.error("audit.falhou entidade={} acao={}", auditable.entity(), auditable.action(), e);
        } finally {
            AuditContext.clear();
        }
        return resultado;
    }

    private void registrar(ProceedingJoinPoint ponto, Auditable auditable, Object resultado) {
        Method metodo = ((MethodSignature) ponto.getSignature()).getMethod();
        Origem origem = origemDaRequisicao();

        recorder.execute(new AuditRequest(
                auditable.entity(),
                idDaEntidade(auditable, metodo, ponto.getArgs(), resultado),
                auditable.action(),
                origem.ip(), origem.userAgent(), TraceIdFilter.currentTraceId(),
                redigir(comoMapa(AuditContext.consume())),
                redigir(comoMapa(resultado))));
    }

    /**
     * Avalia a expressão da anotação com {@code #result} e os parâmetros.
     *
     * <p>Erro de expressão vira {@code null} e um log: o id errado quebraria a
     * gravação, e uma anotação mal escrita não pode derrubar o caso de uso que
     * ela só deveria observar.
     */
    private UUID idDaEntidade(Auditable auditable, Method metodo, Object[] args, Object resultado) {
        try {
            MethodBasedEvaluationContext contexto =
                    new MethodBasedEvaluationContext(TypedValue.NULL, metodo, args, PARAMETROS);
            contexto.setVariable("result", resultado);

            Object valor = PARSER.parseExpression(auditable.id()).getValue(contexto);
            return switch (valor) {
                case UUID uuid -> uuid;
                case String texto -> UUID.fromString(texto);
                case null -> null;
                default -> null;
            };
        } catch (EvaluationException | IllegalArgumentException e) {
            log.error("audit.expressao_invalida expressao={} entidade={}",
                    auditable.id(), auditable.entity(), e);
            return null;
        }
    }

    private Map<String, Object> comoMapa(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(valor, MAPA);
        } catch (IllegalArgumentException e) {
            log.error("audit.retorno_nao_serializavel tipo={}", valor.getClass().getName(), e);
            return null;
        }
    }

    /** Aplica a máscara recursivamente — o campo sensível pode estar aninhado. */
    private Map<String, Object> redigir(Map<String, Object> dados) {
        if (dados == null || dados.isEmpty()) {
            return dados;
        }
        Map<String, Object> limpo = new LinkedHashMap<>();
        dados.forEach((chave, valor) -> limpo.put(chave, redigirValor(chave, valor)));
        return limpo;
    }

    @SuppressWarnings("unchecked")
    private Object redigirValor(String chave, Object valor) {
        if (REDIGIDOS.contains(chave)) {
            return MASCARA;
        }
        if (valor instanceof Map<?, ?> mapa) {
            return redigir((Map<String, Object>) mapa);
        }
        if (valor instanceof List<?> lista) {
            return lista.stream().map(item -> redigirValor(chave, item)).toList();
        }
        return valor;
    }

    /**
     * IP e user-agent só existem quando há requisição HTTP por trás.
     *
     * <p>Job agendado audita sem origem — e a ausência dos dois campos é o que
     * diferencia "o sistema fez" de "alguém fez".
     */
    private Origem origemDaRequisicao() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            return new Origem(ips.resolve(request), recortar(request.getHeader("User-Agent")));
        }
        return new Origem(null, null);
    }

    /** A coluna tem 500; user-agent é entrada do usuário e não tem limite. */
    private static String recortar(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
    }

    private record Origem(String ip, String userAgent) {
    }
}
