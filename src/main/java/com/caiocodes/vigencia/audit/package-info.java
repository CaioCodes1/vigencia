/**
 * Trilha de auditoria — quem fez o quê, quando e de onde.
 *
 * <p>Responde a pergunta que aparece quando o contrato foi renovado por um
 * valor que o cliente contesta: "quem mudou isto?". A planilha nunca respondeu,
 * e é por isso que a auditoria é requisito, não enfeite.
 *
 * <p>Três decisões dão forma ao módulo:
 *
 * <ul>
 *   <li><b>É append-only no banco</b>, garantido por gatilho e por
 *       {@code REVOKE} (migração V6) — não por disciplina de quem programa.</li>
 *   <li><b>É preenchida por AOP</b> ({@code @Auditable}), porque requisito
 *       transversal escrito à mão some no caso de uso número 27.</li>
 *   <li><b>Não tem camada de domínio de verdade</b>: um {@code record}, uma
 *       porta e um adaptador JDBC. Não há invariante a proteger em algo que
 *       nasce pronto e nunca muda.</li>
 * </ul>
 *
 * <p>Fase 7 do roadmap.
 */
package com.caiocodes.vigencia.audit;
