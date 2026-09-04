package com.caiocodes.crbap.billing.domain;

import com.caiocodes.crbap.shared.domain.Money;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingTest {

    private static final Instant AGORA = Instant.parse("2026-09-03T12:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 3);
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 9, 10);
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID CONTRATO = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();

    @Nested
    @DisplayName("pagamento")
    class Pagamentos {

        @Test
        @DisplayName("pagamento parcial deixa a cobrança PARTIALLY_PAID com o saldo certo")
        void deve_registrar_pagamento_parcial() {
            Billing cobranca = umaCobranca("1000.00");

            cobranca.registerPayment(Money.of("400.00", "BRL"), PaymentMethod.PIX, AGORA,
                    null, "chave-1", OPERADOR);

            assertThat(cobranca.status()).isEqualTo(BillingStatus.PARTIALLY_PAID);
            assertThat(cobranca.totalPaid()).isEqualTo(Money.of("400.00", "BRL"));
            assertThat(cobranca.remaining()).isEqualTo(Money.of("600.00", "BRL"));
        }

        @Test
        @DisplayName("o resto quita: dois parciais somando o total dão PAID")
        void dois_parciais_devem_quitar() {
            Billing cobranca = umaCobranca("1000.00");
            cobranca.registerPayment(Money.of("400.00", "BRL"), PaymentMethod.PIX, AGORA,
                    null, "chave-1", OPERADOR);

            cobranca.registerPayment(Money.of("600.00", "BRL"), PaymentMethod.BOLETO, AGORA,
                    null, "chave-2", OPERADOR);

            assertThat(cobranca.status()).isEqualTo(BillingStatus.PAID);
            assertThat(cobranca.remaining().isZero()).isTrue();
        }

        @Test
        @DisplayName("pagamento acima do saldo é recusado — crédito é outro assunto")
        void nao_deve_aceitar_pagamento_acima_do_saldo() {
            Billing cobranca = umaCobranca("1000.00");
            cobranca.registerPayment(Money.of("900.00", "BRL"), PaymentMethod.PIX, AGORA,
                    null, "chave-1", OPERADOR);

            assertThatThrownBy(() -> cobranca.registerPayment(Money.of("200.00", "BRL"),
                    PaymentMethod.PIX, AGORA, null, "chave-2", OPERADOR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("excede o saldo");

            assertThat(cobranca.payments()).hasSize(1);
        }

        @Test
        @DisplayName("cobrança cancelada não recebe pagamento")
        void nao_deve_pagar_cobranca_cancelada() {
            Billing cobranca = umaCobranca("1000.00");
            cobranca.cancel("Emitida em duplicidade pelo financeiro");

            assertThatThrownBy(() -> cobranca.registerPayment(Money.of("100.00", "BRL"),
                    PaymentMethod.PIX, AGORA, null, "chave-1", OPERADOR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cancelada");
        }

        @Test
        @DisplayName("pagamento sem chave de idempotência é recusado")
        void deve_exigir_chave_de_idempotencia() {
            Billing cobranca = umaCobranca("1000.00");

            assertThatThrownBy(() -> cobranca.registerPayment(Money.of("100.00", "BRL"),
                    PaymentMethod.PIX, AGORA, null, "  ", OPERADOR))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("chave de idempotência");
        }
    }

    @Nested
    @DisplayName("estorno")
    class Estornos {

        @Test
        @DisplayName("estornar não apaga: marca o pagamento e devolve o saldo")
        void deve_estornar_sem_apagar() {
            Billing cobranca = umaCobranca("1000.00");
            var pagamento = cobranca.registerPayment(Money.of("1000.00", "BRL"),
                    PaymentMethod.PIX, AGORA, null, "chave-1", OPERADOR);
            assertThat(cobranca.status()).isEqualTo(BillingStatus.PAID);

            cobranca.refundPayment(pagamento.id(), "Pagamento em duplicidade no banco",
                    AGORA, HOJE);

            assertThat(cobranca.payments())
                    .as("o pagamento continua no histórico, marcado")
                    .hasSize(1);
            assertThat(cobranca.payments().get(0).isRefunded()).isTrue();
            assertThat(cobranca.status()).isEqualTo(BillingStatus.PENDING);
            assertThat(cobranca.remaining()).isEqualTo(Money.of("1000.00", "BRL"));
        }

        @Test
        @DisplayName("estorno de cobrança já vencida devolve para OVERDUE, não para PENDING")
        void estorno_de_vencida_deve_voltar_para_overdue() {
            Billing cobranca = umaCobranca("1000.00");
            var pagamento = cobranca.registerPayment(Money.of("1000.00", "BRL"),
                    PaymentMethod.PIX, AGORA, null, "chave-1", OPERADOR);
            LocalDate depoisDoVencimento = VENCIMENTO.plusDays(5);

            cobranca.refundPayment(pagamento.id(), "Estorno solicitado pelo cliente",
                    AGORA, depoisDoVencimento);

            assertThat(cobranca.status()).isEqualTo(BillingStatus.OVERDUE);
        }

        @Test
        @DisplayName("estornar duas vezes o mesmo pagamento é recusado")
        void nao_deve_estornar_duas_vezes() {
            Billing cobranca = umaCobranca("1000.00");
            var pagamento = cobranca.registerPayment(Money.of("1000.00", "BRL"),
                    PaymentMethod.PIX, AGORA, null, "chave-1", OPERADOR);
            cobranca.refundPayment(pagamento.id(), "Pagamento em duplicidade no banco",
                    AGORA, HOJE);

            assertThatThrownBy(() -> cobranca.refundPayment(pagamento.id(),
                    "Tentando de novo por engano", AGORA, HOJE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("já foi estornado");
        }
    }

    @Nested
    @DisplayName("vencimento e cancelamento")
    class CicloDeVida {

        @Test
        @DisplayName("passou do vencimento vira OVERDUE")
        void deve_marcar_vencida() {
            Billing cobranca = umaCobranca("1000.00");

            boolean mudou = cobranca.markOverdue(VENCIMENTO.plusDays(1));

            assertThat(mudou).isTrue();
            assertThat(cobranca.status()).isEqualTo(BillingStatus.OVERDUE);
            assertThat(cobranca.daysLate(VENCIMENTO.plusDays(3))).isEqualTo(3);
        }

        @Test
        @DisplayName("no dia do vencimento ainda não está vencida")
        void nao_deve_vencer_no_proprio_dia() {
            Billing cobranca = umaCobranca("1000.00");

            assertThat(cobranca.markOverdue(VENCIMENTO)).isFalse();
            assertThat(cobranca.status()).isEqualTo(BillingStatus.PENDING);
        }

        @Test
        @DisplayName("markOverdue devolve false em vez de lançar — é chamado em lote")
        void marcar_vencida_o_que_nao_se_aplica_nao_deve_lancar() {
            Billing paga = umaCobranca("1000.00");
            paga.registerPayment(Money.of("1000.00", "BRL"), PaymentMethod.PIX, AGORA,
                    null, "chave-1", OPERADOR);

            // Num lote de milhares, "essa aqui não se aplica" é o caso normal,
            // não um erro. Exceção para o inesperado, retorno para o esperado.
            assertThat(paga.markOverdue(VENCIMENTO.plusDays(30))).isFalse();
            assertThat(paga.status()).isEqualTo(BillingStatus.PAID);
        }

        @Test
        @DisplayName("cobrança com pagamento não é cancelável — estorne primeiro")
        void nao_deve_cancelar_cobranca_com_pagamento() {
            Billing cobranca = umaCobranca("1000.00");
            cobranca.registerPayment(Money.of("100.00", "BRL"), PaymentMethod.PIX, AGORA,
                    null, "chave-1", OPERADOR);

            assertThatThrownBy(() -> cobranca.cancel("Cliente pediu para cancelar a fatura"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("estorne primeiro");
        }

        @Test
        @DisplayName("cancelar exige motivo com pelo menos 10 caracteres")
        void deve_exigir_motivo_no_cancelamento() {
            Billing cobranca = umaCobranca("1000.00");

            assertThatThrownBy(() -> cobranca.cancel("erro"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("invariantes")
    class Invariantes {

        @Test
        @DisplayName("valor zero é recusado na emissão")
        void nao_deve_emitir_cobranca_de_valor_zero() {
            assertThatThrownBy(() -> Billing.issue(new NewBilling(CLIENTE, CONTRATO, "CT-1 1/1",
                    1, 1, Money.of("0.00", "BRL"), VENCIMENTO, HOJE, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maior que zero");
        }

        @Test
        @DisplayName("cobrança avulsa não tem parcela")
        void avulsa_nao_deve_ter_parcela() {
            assertThatThrownBy(() -> Billing.issue(new NewBilling(CLIENTE, null, "Multa",
                    1, 12, Money.of("100.00", "BRL"), VENCIMENTO, HOJE, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("avulsa");
        }

        @Test
        @DisplayName("parcela sem total (ou o contrário) é recusada")
        void parcela_e_total_devem_andar_juntos() {
            assertThatThrownBy(() -> Billing.issue(new NewBilling(CLIENTE, CONTRATO, "CT-1",
                    3, null, Money.of("100.00", "BRL"), VENCIMENTO, HOJE, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("andam juntos");
        }

        @Test
        @DisplayName("nasce PENDING, com saldo igual ao valor")
        void deve_nascer_pendente() {
            Billing cobranca = umaCobranca("1000.00");

            assertThat(cobranca.status()).isEqualTo(BillingStatus.PENDING);
            assertThat(cobranca.totalPaid().isZero()).isTrue();
            assertThat(cobranca.remaining()).isEqualTo(cobranca.amount());
        }
    }

    private static Billing umaCobranca(String valor) {
        Billing billing = Billing.issue(new NewBilling(CLIENTE, CONTRATO, "CT-2026-0042 1/12",
                1, 12, Money.of(valor, "BRL"), VENCIMENTO, HOJE, null, null));
        billing.pullEvents();
        return billing;
    }
}
