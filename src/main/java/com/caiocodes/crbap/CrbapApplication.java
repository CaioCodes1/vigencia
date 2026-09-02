package com.caiocodes.crbap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da Contract Renewal &amp; Billing Automation Platform.
 *
 * <p>A organização é por domínio (iam, client, contract, billing, notification,
 * reporting) e, dentro de cada um, por camada (domain, application,
 * infrastructure, web). A regra que sustenta o desenho: a dependência sempre
 * aponta para dentro — o pacote {@code domain} não conhece Spring, JPA nem HTTP.
 * Quem garante isso é o {@code ArchitectureTest}, não o code review.
 */
@SpringBootApplication
public class CrbapApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrbapApplication.class, args);
    }
}
