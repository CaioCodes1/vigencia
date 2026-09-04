package com.caiocodes.crbap.billing.domain;

import com.caiocodes.crbap.billing.domain.BillingScheduleCalculator.BillingDraft;
import com.caiocodes.crbap.shared.domain.BillingCycle;
import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste mais importante da fase 5.
 *
 * <p>A regra de ouro: <b>todo cálculo de dinheiro tem um teste que confere a
 * soma</b>. É o invariante que pega o erro de arredondamento — o bug que não
 * quebra nada, não gera exceção e só aparece meses depois, quando o relatório de
 * receita fecha com centavos de diferença que ninguém consegue explicar.
 */
class BillingScheduleCalculatorTest {

    private static final DateRange UM_ANO =
            DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

    @ParameterizedTest
    @CsvSource({
        "1000.00, MONTHLY",
        "1000.00, QUARTERLY",
        "24000.00, MONTHLY",
        "999.99, MONTHLY",
        "0.05, MONTHLY",
        "12345.67, QUARTERLY",
        "7777.77, SEMIANNUAL"
    })
    @DisplayName("a soma das parcelas é EXATAMENTE o valor do contrato")
    void a_soma_das_parcelas_deve_bater_com_o_total(String valor, BillingCycle ciclo) {
        Money total = Money.of(valor, "BRL");

        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                UM_ANO, total, ciclo, null, 0);

        Money somado = parcelas.stream()
                .map(BillingDraft::amount)
                .reduce(Money.zero(total.currency()), Money::add);

        assertThat(somado)
                .as("nenhum centavo pode nascer nem sumir na divisão")
                .isEqualTo(total);
    }

    @Test
    @DisplayName("o centavo que sobra vai para a ÚLTIMA parcela, não para a primeira")
    void a_sobra_deve_cair_na_ultima_parcela() {
        // 1000,00 / 3 = 333,333… A primeira parcela é a que o cliente vê ao
        // assinar: um valor quebrado logo de cara gera ligação para o comercial.
        DateRange trimestre = DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                trimestre, Money.of("1000.00", "BRL"), BillingCycle.MONTHLY, null, 0);

        assertThat(parcelas).hasSize(3);
        assertThat(parcelas.get(0).amount().amount()).isEqualByComparingTo("333.33");
        assertThat(parcelas.get(1).amount().amount()).isEqualByComparingTo("333.33");
        assertThat(parcelas.get(2).amount().amount()).isEqualByComparingTo("333.34");
    }

    @Test
    @DisplayName("pagamento único gera uma cobrança só, vencendo no início da vigência")
    void pagamento_unico_deve_gerar_uma_cobranca() {
        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                UM_ANO, Money.of("5000.00", "BRL"), BillingCycle.ONE_TIME, null, 0);

        assertThat(parcelas).hasSize(1);
        assertThat(parcelas.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(parcelas.get(0).amount().amount()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("as parcelas são numeradas 1..N e os vencimentos andam com o ciclo")
    void deve_numerar_e_espacar_as_parcelas() {
        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                UM_ANO, Money.of("1200.00", "BRL"), BillingCycle.MONTHLY, null, 0);

        assertThat(parcelas).hasSize(12);
        assertThat(parcelas.get(0).installment()).isEqualTo(1);
        assertThat(parcelas.get(11).installment()).isEqualTo(12);
        assertThat(parcelas.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(parcelas.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(parcelas.get(11).dueDate()).isEqualTo(LocalDate.of(2027, 9, 1));
    }

    @Test
    @DisplayName("o dia de cobrança fixa o vencimento no mês")
    void deve_respeitar_o_dia_de_cobranca() {
        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                UM_ANO, Money.of("1200.00", "BRL"), BillingCycle.MONTHLY, 15, 0);

        assertThat(parcelas.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(parcelas.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("o dia de cobrança nunca empurra o vencimento para antes da parcela")
    void dia_de_cobranca_anterior_ao_inicio_deve_cair_no_mes_seguinte() {
        // Contrato que começa dia 20 com billingDay 10 venceria dia 10 — dez
        // dias antes de o serviço existir.
        DateRange comecaDia20 =
                DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 19));

        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                comecaDia20, Money.of("1200.00", "BRL"), BillingCycle.MONTHLY, 10, 0);

        assertThat(parcelas.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName("a carência adia o vencimento sem mexer no valor")
    void deve_aplicar_a_carencia() {
        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                UM_ANO, Money.of("1200.00", "BRL"), BillingCycle.MONTHLY, null, 5);

        assertThat(parcelas.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(parcelas.get(0).amount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("um contrato de R$ 0,01 em 12 parcelas não gera parcela de zero")
    void nao_deve_gerar_parcela_de_valor_zero() {
        // Caso degenerado, mas real em teste de integração: 0,01 dividido por 12
        // dá 0,00 em 11 parcelas. Cobrança de valor zero é recusada pelo
        // agregado, então o cálculo não pode produzi-la em silêncio.
        List<BillingDraft> parcelas = BillingScheduleCalculator.from(
                UM_ANO, Money.of("0.01", "BRL"), BillingCycle.MONTHLY, null, 0);

        Money somado = parcelas.stream()
                .map(BillingDraft::amount)
                .reduce(Money.zero(java.util.Currency.getInstance("BRL")), Money::add);

        assertThat(somado.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(parcelas.stream().filter(p -> p.amount().isZero()).count())
                .as("parcela de zero é recusada pelo agregado — o cálculo não pode criá-la")
                .isZero();
    }
}
