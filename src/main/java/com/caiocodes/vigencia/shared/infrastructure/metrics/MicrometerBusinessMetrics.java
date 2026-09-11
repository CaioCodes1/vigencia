package com.caiocodes.vigencia.shared.infrastructure.metrics;

import com.caiocodes.vigencia.shared.application.BusinessMetrics;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Implementação da porta {@link BusinessMetrics} sobre o Micrometer. */
@Component
public class MicrometerBusinessMetrics implements BusinessMetrics {

    private final MeterRegistry registry;
    private final Clock clock;
    private final DistributionSummary valorRenovado;

    /**
     * Um relógio por job, criado na primeira execução bem-sucedida.
     *
     * <p>O conjunto de nomes de job é fechado e pequeno (três hoje), então cada
     * um virar uma série é seguro — é o oposto de usar id como rótulo.
     */
    private final Map<String, AtomicLong> ultimoSucesso = new ConcurrentHashMap<>();

    public MicrometerBusinessMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.valorRenovado = DistributionSummary.builder("vigencia.contracts.renewed.value")
                .description("Valor dos contratos renovados")
                .baseUnit("BRL")
                .register(registry);
    }

    @Override
    public void notificationSent(String type, String channel, boolean success) {
        registry.counter("vigencia.notifications.sent",
                "type", type,
                "channel", channel,
                "result", success ? "success" : "failure").increment();
    }

    @Override
    public void contractRenewed(BigDecimal value) {
        registry.counter("vigencia.contracts.renewed").increment();
        if (value != null) {
            valorRenovado.record(value.doubleValue());
        }
    }

    /**
     * Grava o instante como <i>gauge</i>, não como contador.
     *
     * <p>O alerta pergunta "faz quanto tempo?" — {@code time() - <gauge>} — e
     * isso só funciona com o carimbo absoluto. Um contador de execuções
     * responderia "quantas vezes rodou desde que subiu", que zera a cada
     * reinício e não diz nada sobre ontem.
     */
    @Override
    public void jobSucceeded(String job) {
        ultimoSucesso.computeIfAbsent(job, nome -> {
            AtomicLong marca = new AtomicLong();
            registry.gauge("vigencia.scheduler.last_success_timestamp",
                    List.of(Tag.of("job", nome)), marca, AtomicLong::doubleValue);
            return marca;
        }).set(clock.instant().getEpochSecond());
    }
}
