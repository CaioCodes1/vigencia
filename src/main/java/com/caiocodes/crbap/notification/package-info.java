/**
 * Notificações — os avisos automáticos de vencimento (D-30, D-15, D-7, D-1) e
 * a régua de cobrança.
 *
 * <p>O canal é uma porta ({@code NotificationSender}) com implementações para
 * log, e-mail e webhook: trocar de canal é configuração, não {@code if}.
 * O índice único {@code (contract_id, type, days_offset)} garante que o mesmo
 * aviso não sai duas vezes, mesmo se o job rodar duas vezes.
 *
 * <p>Fase 6 do roadmap.
 */
package com.caiocodes.crbap.notification;
