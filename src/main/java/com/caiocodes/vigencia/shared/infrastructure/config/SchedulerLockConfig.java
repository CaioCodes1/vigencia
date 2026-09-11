package com.caiocodes.vigencia.shared.infrastructure.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Trava distribuída dos jobs.
 *
 * <p><b>O problema:</b> com três instâncias no ar, {@code @Scheduled} dispara
 * nas três às 3h. Sem trava, o cliente recebe três e-mails de "seu contrato
 * vence em 30 dias" — e a primeira coisa que ele faz é perder a confiança nos
 * outros avisos.
 *
 * <p>A trava vive numa linha da tabela {@code shedlock}: a primeira instância a
 * gravar ganha, as outras pulam sem esperar. Não é fila, é exclusão.
 *
 * <p>Dois parâmetros que parecem detalhe e não são:
 *
 * <ul>
 *   <li><b>{@code lockAtLeastFor}</b> segura a trava mesmo se o job terminar em
 *       dois segundos. Sem ele, com relógios levemente diferentes entre as
 *       máquinas, a segunda instância pega a trava logo em seguida e roda tudo
 *       de novo — o efeito que a trava existia para evitar.</li>
 *   <li><b>{@code lockAtMostFor}</b> é a validade máxima: se a instância morrer
 *       no meio do job, a trava expira sozinha e outra assume. Sempre maior que
 *       a pior duração esperada, senão duas instâncias rodam em paralelo
 *       justamente no dia em que o job demorou mais que o normal.</li>
 * </ul>
 *
 * <p>O {@code defaultLockAtMostFor} aqui é a rede de segurança; cada job declara
 * o seu, dimensionado ao que ele faz.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@ConditionalOnProperty(name = "vigencia.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        // Sem isto o ShedLock usa o relógio da JVM, e três
                        // instâncias com relógios diferentes calculam janelas
                        // diferentes. Com o relógio do banco, existe uma
                        // referência só — que é o ponto de ter uma trava
                        // central.
                        .usingDbTime()
                        .build());
    }


}
