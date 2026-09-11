/**
 * Notificações — a régua de avisos e o log de envio.
 *
 * <p><b>É aqui que o produto passa a existir.</b> Cadastro, contrato e cobrança
 * a planilha também fazia, mal. O que ela nunca fez foi avisar sozinha.
 *
 * <p>Duas peças independentes de propósito: a <b>varredura</b> descobre quem
 * precisa ser avisado e grava as linhas em {@code PENDING}; o <b>envio</b> pega
 * as pendentes e manda. O SMTP cair não pode impedir o sistema de saber quem
 * precisa ser avisado.
 *
 * <p>A garantia de não avisar duas vezes é do banco — índice único por
 * (contrato, tipo, janela, destinatário) — e não de um {@code SELECT} antes do
 * {@code INSERT}, que não sobrevive a duas instâncias rodando o job ao mesmo
 * tempo.
 *
 * <p>Este módulo <b>declara</b> a porta {@code ExpiringContractsPort} e é o
 * módulo {@code contract} quem a implementa: "quem eu preciso avisar hoje?" é
 * pergunta de notificações, e contratos não sabem que existe e-mail.
 *
 * <p>Fase 6 do roadmap.
 */
package com.caiocodes.vigencia.notification;
