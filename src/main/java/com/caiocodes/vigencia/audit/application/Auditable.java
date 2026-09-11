package com.caiocodes.vigencia.audit.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um caso de uso cuja execução vai para a trilha de auditoria.
 *
 * <p><b>Por que AOP e não uma chamada explícita:</b> auditoria é um requisito
 * transversal — vale para todo caso de uso que muda dado, e vale igual. Escrita
 * na mão, ela some no caso de uso número 27, escrito numa sexta-feira, e o
 * buraco só aparece na primeira auditoria de verdade. Como anotação, esquecer
 * é visível: o método não tem a linha, e a revisão enxerga a ausência.
 *
 * <p>O preço do AOP é o de sempre — o comportamento não está onde o código
 * está. Por isso ele fica reduzido ao mínimo: uma anotação declarativa, um
 * aspecto só, e nada de lógica de negócio dentro dele.
 *
 * <p>Exemplo:
 * <pre>{@code
 * @Auditable(entity = "Contract", action = "RENEW",
 *            id = "#result.contract().id()")
 * public RenewalResult execute(RenewContract command) { ... }
 * }</pre>
 *
 * @see AuditContext para registrar o estado <i>anterior</i> de uma edição
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** O tipo da entidade tocada — {@code "Contract"}, {@code "Client"}. */
    String entity();

    /** O verbo: {@code CREATE}, {@code UPDATE}, {@code RENEW}, {@code CANCEL}. */
    String action();

    /**
     * Expressão SpEL que devolve o id da entidade.
     *
     * <p>Tem acesso a {@code #result} (o retorno do método) e aos parâmetros
     * pelo nome ({@code #command}, {@code #id}). Em método {@code void},
     * {@code #result} é nulo e o id tem que vir dos argumentos.
     *
     * <p>É expressão escrita por quem programa, nunca entrada de usuário — é
     * o que torna o SpEL aceitável aqui: SpEL avaliando texto vindo de
     * requisição é execução remota de código.
     */
    String id() default "#result.id()";
}
