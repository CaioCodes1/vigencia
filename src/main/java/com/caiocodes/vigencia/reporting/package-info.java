/**
 * Relatórios e dashboard — o único módulo sem camada de domínio, de propósito.
 *
 * <p>Relatório não tem invariante nem máquina de estados: é
 * {@code SELECT ... GROUP BY}. Forçar agregado e repositório aqui produziria
 * cerimônia e query lenta. Por isso este módulo usa JDBC com SQL escrito à mão.
 * É CQRS light: a escrita passa pelo domínio, a leitura analítica vai reto ao
 * banco.
 *
 * <p>Fase 7 do roadmap.
 */
package com.caiocodes.vigencia.reporting;
