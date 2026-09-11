/**
 * Contratos — o núcleo do sistema.
 *
 * <p>Agregado {@code Contract} com máquina de estados
 * (DRAFT → ACTIVE → RENEWED/EXPIRED/CANCELLED), renovação encadeada
 * ({@code previousContractId}) e a varredura diária de vencimentos.
 *
 * <p>Camadas, de dentro para fora:
 * <ul>
 *   <li>{@code domain} — Java puro: agregado, value objects, portas e eventos.
 *       Zero import de Spring, JPA ou Jackson;</li>
 *   <li>{@code application} — um caso de uso por classe, com {@code @Transactional};</li>
 *   <li>{@code infrastructure} — JPA, mappers, scheduler, mensageria;</li>
 *   <li>{@code web} — controllers e DTOs HTTP.</li>
 * </ul>
 *
 * <p>Fase 4 do roadmap.
 */
package com.caiocodes.vigencia.contract;
