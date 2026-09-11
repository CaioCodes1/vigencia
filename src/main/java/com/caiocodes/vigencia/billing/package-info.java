/**
 * Cobranças e pagamentos — geração das parcelas, recebimento e estorno.
 *
 * <p>As parcelas de um contrato nascem na <b>ativação</b>, na mesma transação
 * (ADR-006): um contrato ativo sem cobrança é uma empresa que parou de faturar
 * sem ninguém perceber. Pela API só se cria cobrança <i>avulsa</i> — multa,
 * serviço extra, acordo.
 *
 * <p>O saldo nunca é coluna: é sempre {@code valor − soma dos pagamentos não
 * estornados}. Estornar não apaga o pagamento, marca — dinheiro que entrou e
 * sumiu do histórico é conciliação bancária que não fecha.
 *
 * <p>Este módulo depende de {@code contract} (implementa a porta
 * {@code ContractBillingPort}) e do {@code ClientDirectory} compartilhado. A
 * seta aponta só nesta direção; o {@code ArchitectureTest} quebra o build se
 * inverter.
 *
 * <p>Fase 5 do roadmap.
 */
package com.caiocodes.vigencia.billing;
