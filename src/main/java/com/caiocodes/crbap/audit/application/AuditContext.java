package com.caiocodes.crbap.audit.application;

/**
 * O estado <i>anterior</i> de uma edição, entregue ao aspecto pelo caso de uso.
 *
 * <p><b>Por que isto existe:</b> o aspecto consegue capturar o "depois"
 * sozinho — é o retorno do método. O "antes" ele não tem como obter de forma
 * genérica: precisaria saber qual repositório carrega qual entidade a partir de
 * qual argumento, para cada módulo. Isso é um mapeador de entidades escondido
 * dentro de um aspecto, e é assim que auditoria por AOP vira um framework
 * paralelo que ninguém entende.
 *
 * <p>A alternativa escolhida é explícita e de uma linha: o caso de uso, que
 * <i>já carregou</i> o agregado, tira uma foto antes de mexer.
 *
 * <pre>{@code
 * Contract atual = finder.requireForUpdate(command.contractId());
 * AuditContext.before(ContractDetail.from(atual, clientName, hoje));
 * }</pre>
 *
 * <p><b>Guarde o mesmo tipo que o método devolve.</b> Quem transforma os dois
 * em JSON é o mesmo {@code ObjectMapper}, no aspecto — e é isso que faz o
 * {@code changedFields} comparar maçã com maçã. Uma foto montada à mão, com
 * {@code Map.of("valor", ...)}, acusaria diferença em todo campo cujo formato
 * não batesse com o da serialização.
 *
 * <p><b>Limite conhecido:</b> a foto vive numa {@code ThreadLocal} e é
 * consumida pelo primeiro aspecto que terminar. Caso de uso auditado chamando
 * outro caso de uso auditado na mesma thread perde a foto do de fora. Hoje isso
 * não acontece — cada caso de uso deste sistema é chamado da borda — e a
 * alternativa (uma pilha de escopos) só se paga quando o aninhamento existir.
 */
public final class AuditContext {

    private static final ThreadLocal<Object> BEFORE = new ThreadLocal<>();

    private AuditContext() {
    }

    /** Registra o estado anterior. Chamar antes de mudar o agregado. */
    public static void before(Object snapshot) {
        BEFORE.set(snapshot);
    }

    /**
     * Lê e limpa. Só o aspecto chama.
     *
     * <p>Ler sem limpar deixaria a foto grudada na thread, que volta ao pool e
     * audita a próxima requisição com o "antes" da anterior — o mesmo modo de
     * falha do MDC sem {@code remove()}.
     */
    public static Object consume() {
        Object snapshot = BEFORE.get();
        BEFORE.remove();
        return snapshot;
    }

    /** Descarte incondicional, para o {@code finally} do aspecto. */
    public static void clear() {
        BEFORE.remove();
    }
}
