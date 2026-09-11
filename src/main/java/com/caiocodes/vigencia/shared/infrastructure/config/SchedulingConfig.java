package com.caiocodes.vigencia.shared.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga os jobs agendados (relay da outbox, varredura de vencimentos).
 *
 * <p>A chave {@code vigencia.jobs.enabled} existe para os testes de integração:
 * com o agendador ligado, um job dispararia no meio de um teste e mudaria
 * dados que o teste está conferindo — a receita clássica de suíte
 * intermitente. Os testes desligam o agendador e chamam o caso de uso
 * diretamente, que é onde a regra mora.
 *
 * <p>Em produção com mais de uma instância isto roda em todas elas. Para o
 * relay tudo bem (o {@code SKIP LOCKED} divide a fila); para a varredura
 * diária, a proteção é o próprio agregado, que recusa expirar um contrato que
 * já não está ativo.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "vigencia.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
