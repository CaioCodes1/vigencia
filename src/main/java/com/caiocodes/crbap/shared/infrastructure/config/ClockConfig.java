package com.caiocodes.crbap.shared.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expõe o relógio como bean.
 *
 * <p>Parece exagero até a primeira vez que você precisa testar "contrato que
 * vence em 30 dias" ou "expirado há 31 dias". Com {@code LocalDate.now()}
 * espalhado pelo código, esse teste exige mexer no relógio da máquina; com o
 * {@code Clock} injetado, é {@code Clock.fixed(...)} — um argumento.
 *
 * <p>O fuso é o de São Paulo porque as janelas de aviso (D-30, D-15, D-7, D-1)
 * são datas de negócio, não instantes: "vence dia 30" precisa virar em Brasília,
 * não em UTC.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
