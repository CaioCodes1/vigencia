package com.caiocodes.vigencia.shared.domain;

import com.caiocodes.vigencia.shared.domain.exception.InvalidDateRangeException;
import java.time.LocalDate;
import java.time.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 1);

    @Test
    void deve_recusar_fim_anterior_ao_inicio() {
        assertThatThrownBy(() -> new DateRange(HOJE, HOJE.minusDays(1)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("início igual ao fim também é recusado — vigência de zero dia não existe")
    void deve_recusar_inicio_igual_ao_fim() {
        assertThatThrownBy(() -> new DateRange(HOJE, HOJE))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @ParameterizedTest(name = "vence em {0} dias")
    @ValueSource(ints = {1, 7, 15, 30, 60})
    void deve_calcular_dias_ate_o_fim(int dias) {
        DateRange periodo = new DateRange(HOJE.minusYears(1), HOJE.plusDays(dias));

        assertThat(periodo.daysUntilEnd(HOJE)).isEqualTo(dias);
    }

    @Test
    @DisplayName("depois de vencido, o número de dias fica negativo")
    void deve_devolver_dias_negativos_apos_o_vencimento() {
        DateRange periodo = new DateRange(HOJE.minusYears(1), HOJE.minusDays(31));

        assertThat(periodo.daysUntilEnd(HOJE)).isEqualTo(-31);
        assertThat(periodo.isExpiredOn(HOJE)).isTrue();
    }

    @Test
    @DisplayName("no último dia de vigência o contrato ainda não expirou")
    void ultimo_dia_nao_e_expirado() {
        DateRange periodo = new DateRange(HOJE.minusMonths(1), HOJE);

        assertThat(periodo.isExpiredOn(HOJE)).isFalse();
        assertThat(periodo.isExpiredOn(HOJE.plusDays(1))).isTrue();
        assertThat(periodo.contains(HOJE)).isTrue();
    }

    @Test
    @DisplayName("o período seguinte começa no dia após o fim, sem sobreposição nem buraco")
    void deve_encadear_o_proximo_periodo() {
        DateRange atual = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

        DateRange proximo = atual.nextPeriod(Period.ofYears(1));

        assertThat(proximo.start()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(proximo.end()).isEqualTo(LocalDate.of(2028, 9, 30));
    }
}
