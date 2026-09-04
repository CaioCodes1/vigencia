package com.caiocodes.crbap.shared.domain;

import com.caiocodes.crbap.shared.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes do value object que carrega todo o dinheiro do sistema. */
class MoneyTest {

    @Nested
    @DisplayName("construção")
    class Construcao {

        @Test
        void deve_normalizar_a_escala_para_duas_casas() {
            assertThat(Money.of("10", "BRL").amount()).isEqualTo(new BigDecimal("10.00"));
            assertThat(Money.of("10.5", "BRL").amount()).isEqualTo(new BigDecimal("10.50"));
        }

        @Test
        void deve_recusar_mais_de_duas_casas_decimais() {
            assertThatThrownBy(() -> Money.of("10.005", "BRL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 casas");
        }

        @Test
        @DisplayName("valores iguais escritos de formas diferentes devem ser equals")
        void deve_igualar_10_e_10_ponto_00() {
            // Sem a normalização da escala isto seria false, porque
            // new BigDecimal("10.0").equals(new BigDecimal("10.00")) é false.
            assertThat(Money.of("10", "BRL")).isEqualTo(Money.of("10.00", "BRL"));
        }
    }

    @Nested
    @DisplayName("aritmética")
    class Aritmetica {

        @Test
        void deve_somar_e_subtrair() {
            Money a = Money.of("100.50", "BRL");
            Money b = Money.of("50.25", "BRL");

            assertThat(a.add(b)).isEqualTo(Money.of("150.75", "BRL"));
            assertThat(a.subtract(b)).isEqualTo(Money.of("50.25", "BRL"));
        }

        @Test
        void deve_multiplicar_por_inteiro() {
            assertThat(Money.of("199.90", "BRL").multiply(12))
                    .isEqualTo(Money.of("2398.80", "BRL"));
        }

        @Test
        @DisplayName("somar moedas diferentes deve estourar, não gerar número sem sentido")
        void deve_recusar_moedas_diferentes() {
            Money reais = Money.of("100.00", "BRL");
            Money dolares = Money.of("100.00", "USD");

            assertThatThrownBy(() -> reais.add(dolares))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("BRL")
                    .hasMessageContaining("USD");
        }

        @Test
        @DisplayName("o erro clássico do double: 0.1 + 0.2 tem que dar exatamente 0.30")
        void deve_somar_centavos_sem_erro_de_ponto_flutuante() {
            assertThat(Money.of("0.10", "BRL").add(Money.of("0.20", "BRL")))
                    .isEqualTo(Money.of("0.30", "BRL"));
        }
    }

    @Nested
    @DisplayName("comparação")
    class Comparacao {

        @ParameterizedTest(name = "{0} > {1} = {2}")
        @CsvSource({
                "100.00, 50.00,  true",
                "50.00,  100.00, false",
                "100.00, 100.00, false"
        })
        void deve_comparar_valores(String a, String b, boolean esperado) {
            assertThat(Money.of(a, "BRL").isGreaterThan(Money.of(b, "BRL"))).isEqualTo(esperado);
        }

        @Test
        void deve_reconhecer_positivo_zero_e_negativo() {
            assertThat(Money.of("0.01", "BRL").isPositive()).isTrue();
            assertThat(Money.of("0.00", "BRL").isZero()).isTrue();
            assertThat(Money.of("-1.00", "BRL").isNegative()).isTrue();
        }
    }

    @Nested
    @DisplayName("divisão em parcelas")
    class Divisao {

        @ParameterizedTest
        @CsvSource({
            "1000.00, 3", "1000.00, 7", "0.03, 2", "999.99, 12", "24000.00, 12",
            "100.00, 3", "0.01, 1", "12345.67, 11"
        })
        @DisplayName("a soma das parcelas é sempre EXATAMENTE o valor original")
        void a_soma_deve_bater(String valor, int partes) {
            Money total = Money.of(valor, "BRL");

            Money somado = total.split(partes).stream()
                    .reduce(Money.zero(total.currency()), Money::add);

            assertThat(somado).isEqualTo(total);
        }

        @Test
        @DisplayName("a sobra de centavos vai para a última parcela")
        void a_sobra_deve_ir_para_a_ultima() {
            // 1000,00 / 3 = 333,333… Arredondar tudo para 333,33 daria 999,99, e
            // o centavo que falta ninguém consegue explicar seis meses depois.
            assertThat(Money.of("1000.00", "BRL").split(3))
                    .containsExactly(Money.of("333.33", "BRL"), Money.of("333.33", "BRL"),
                            Money.of("333.34", "BRL"));
        }

        @Test
        @DisplayName("dividir em uma parcela devolve o próprio valor")
        void uma_parcela_deve_devolver_o_total() {
            assertThat(Money.of("1000.00", "BRL").split(1))
                    .containsExactly(Money.of("1000.00", "BRL"));
        }

        @Test
        @DisplayName("zero ou menos parcelas é erro de programação, não regra de negócio")
        void nao_deve_dividir_em_zero_partes() {
            assertThatThrownBy(() -> Money.of("100.00", "BRL").split(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("o valor em centavos diz quantas parcelas não-zero cabem")
        void deve_converter_para_centavos() {
            assertThat(Money.of("1234.56", "BRL").toMinorUnits()).isEqualTo(123456L);
            assertThat(Money.of("0.01", "BRL").toMinorUnits()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("toString inclui a moeda — número solto não é dinheiro")
    void to_string_deve_incluir_a_moeda() {
        assertThat(Money.of("1234.50", "BRL")).hasToString("BRL 1234.50");
    }
}
