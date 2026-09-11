package com.caiocodes.vigencia.shared.infrastructure.metrics;

import com.caiocodes.vigencia.shared.infrastructure.persistence.OutboxJpaRepository;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * "O relay ainda está publicando?"
 *
 * <p>Fica em {@code /actuator/health/readiness} e não em {@code liveness}: com
 * a outbox entupida a aplicação está perfeitamente viva — reiniciá-la não
 * resolveria nada e ainda derrubaria as requisições em andamento. O que se quer
 * é tirá-la do balanceador e acordar alguém.
 *
 * <p><b>Conta só o que está parado há mais de cinco minutos.</b> Um lote grande
 * recém-gravado (ativar um contrato anual emite doze eventos de uma vez) faria
 * a contagem crua passar de cem em um instante e o health cairia por um sistema
 * funcionando perfeitamente. Alarme que dispara sozinho é alarme que se aprende
 * a ignorar — e aí o dia em que ele estiver certo ninguém olha.
 */
@Component
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private final OutboxJpaRepository outbox;
    private final Clock clock;

    @Value("${vigencia.jobs.outbox-health-threshold:100}")
    private long limite;

    @Override
    public Health health() {
        long paradas;
        try {
            paradas = outbox.countByPublishedAtIsNullAndOccurredAtBefore(
                    clock.instant().minus(TOLERANCIA));
        } catch (RuntimeException e) {
            // Banco fora do ar já é reportado pelo health do DataSource. Aqui,
            // "não consegui medir" é diferente de "está ruim".
            return Health.unknown().withDetail("erro", e.getMessage()).build();
        }

        if (paradas > limite) {
            return Health.down()
                    .withDetail("pendingEvents", paradas)
                    .withDetail("hint", "OutboxRelay parado ou RabbitMQ fora do ar")
                    .build();
        }
        return Health.up().withDetail("pendingEvents", paradas).build();
    }
}
