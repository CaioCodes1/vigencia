package com.caiocodes.crbap.shared.application;

import java.math.BigDecimal;

/**
 * Os fatos de negócio que viram número no Prometheus.
 *
 * <p><b>Por que uma porta, e não o {@code MeterRegistry} direto:</b> pelo mesmo
 * motivo de {@code CurrentUser} e {@code DomainEventRecorder} — o caso de uso
 * fica testável sem subir contexto, e trocar Micrometer por outra coisa não
 * encosta na camada de aplicação. Também mantém a lista de métricas de negócio
 * <i>visível em um arquivo só</i>: cada método aqui é uma pergunta que alguém
 * vai fazer ao painel às três da manhã.
 *
 * <p><b>Nada de id como parâmetro.</b> Cada combinação de rótulos vira uma série
 * temporal no Prometheus; id de cliente ou de contrato multiplicaria isso por
 * dezenas de milhares e derrubaria o servidor de métricas. Id vai no log, que é
 * indexado por conteúdo. Aqui só entra valor de baixa cardinalidade: tipo,
 * canal, resultado, nome do job.
 */
public interface BusinessMetrics {

    /**
     * Um aviso saiu (ou falhou ao sair).
     *
     * <p>{@code result="failure"} subindo é o pior modo de falha desta
     * plataforma: o sistema acha que avisou, o cliente não recebeu, e ninguém
     * descobre até a renovação ser perdida.
     */
    void notificationSent(String type, String channel, boolean success);

    /** Um contrato foi renovado, e por quanto. */
    void contractRenewed(BigDecimal value);

    /**
     * Um job agendado terminou bem, agora.
     *
     * <p>Vira {@code crbap_scheduler_last_success_timestamp{job="..."}}, que é o
     * alerta que a planilha nunca teve: <b>um aviso de que os avisos pararam</b>.
     * Sem ele, a falha só aparece quando um cliente reclama — semanas depois.
     */
    void jobSucceeded(String job);
}
