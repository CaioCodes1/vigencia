package com.caiocodes.vigencia.reporting.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A defesa de unidade contra o vazamento de escopo pelo cache.
 *
 * <p>O teste de ponta a ponta (o do vendedor que não pode ver a carteira do
 * gestor) mora no {@code DashboardIT}. Este aqui é mais barato e falha mais
 * cedo: se duas carteiras diferentes produzirem a mesma chave, o vazamento
 * existe antes de qualquer requisição.
 */
class DashboardScopeTest {

    @Test
    @DisplayName("carteiras diferentes produzem chaves de cache diferentes")
    void chave_deve_separar_carteiras() {
        DashboardScope bruno = DashboardScope.of(UUID.randomUUID());
        DashboardScope diego = DashboardScope.of(UUID.randomUUID());

        assertThat(bruno.cacheKey()).isNotEqualTo(diego.cacheKey());
    }

    @Test
    @DisplayName("o painel da empresa não compartilha chave com carteira nenhuma")
    void chave_global_deve_ser_propria() {
        UUID gestor = UUID.randomUUID();

        assertThat(DashboardScope.all().cacheKey()).isEqualTo("all");
        assertThat(DashboardScope.of(gestor).cacheKey())
                .isEqualTo("manager:" + gestor)
                .isNotEqualTo(DashboardScope.all().cacheKey());
    }

    @Test
    @DisplayName("a mesma carteira sempre produz a mesma chave — senão o cache nunca acerta")
    void chave_deve_ser_estavel() {
        UUID gestor = UUID.randomUUID();

        assertThat(DashboardScope.of(gestor).cacheKey())
                .isEqualTo(DashboardScope.of(gestor).cacheKey());
    }

    @Test
    @DisplayName("taxa de renovação de período vazio é zero, não divisão por zero")
    void taxa_sem_contrato_encerrado_deve_ser_zero() {
        // O primeiro mês de operação. Existe em toda implantação, e é onde
        // NULLIF(count(*), 0) costuma faltar.
        assertThat(RenewalRate.of("2026-Q1", 0, 0).ratePercent())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("22 renovados e 6 perdidos dão 78,57%")
    void taxa_deve_arredondar_em_duas_casas() {
        assertThat(RenewalRate.of("2026-Q3", 22, 6).ratePercent())
                .isEqualByComparingTo(new BigDecimal("78.57"));
    }
}
