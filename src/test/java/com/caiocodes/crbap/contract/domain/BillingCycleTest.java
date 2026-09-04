package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.DateRange;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BillingCycleTest {

    @ParameterizedTest
    @CsvSource({
        "MONTHLY, 12",
        "QUARTERLY, 4",
        "SEMIANNUAL, 2",
        "YEARLY, 1",
        "ONE_TIME, 1"
    })
    @DisplayName("um contrato de 12 meses gera o número de parcelas do ciclo")
    void deve_contar_parcelas_de_um_ano(BillingCycle ciclo, int esperado) {
        DateRange umAno = DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

        assertThat(ciclo.occurrencesIn(umAno)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("contrato mais curto que o ciclo ainda gera uma cobrança, nunca zero")
    void deve_gerar_ao_menos_uma_parcela() {
        DateRange seisMeses = DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        // Sem o piso de 1, um contrato anual de 6 meses existiria sem nunca ser
        // faturado — e ninguém perceberia até a conciliação do trimestre.
        assertThat(BillingCycle.YEARLY.occurrencesIn(seisMeses)).isEqualTo(1);
    }

    @Test
    @DisplayName("o vencimento da parcela N anda de acordo com o ciclo")
    void deve_calcular_vencimento_da_parcela() {
        LocalDate inicio = LocalDate.of(2026, 10, 1);

        assertThat(BillingCycle.MONTHLY.nextDueDate(inicio, 3))
                .isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(BillingCycle.QUARTERLY.nextDueDate(inicio, 2))
                .isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    @DisplayName("o período seguinte começa no dia após o fim do anterior — sem buraco")
    void deve_encadear_periodos_sem_buraco() {
        DateRange atual = DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

        DateRange proximo = BillingCycle.YEARLY.nextPeriodAfter(atual);

        assertThat(proximo.start()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(proximo.end()).isEqualTo(LocalDate.of(2028, 9, 30));
    }
}
