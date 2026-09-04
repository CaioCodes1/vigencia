package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import com.caiocodes.crbap.shared.domain.exception.IllegalStateTransitionException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A máquina de estados do contrato, transição por transição.
 *
 * <p>Roda em milissegundos e sem Docker: é exatamente o que o domínio isolado
 * de framework compra.
 */
class ContractTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 3);
    private static final Instant AGORA = Instant.parse("2026-09-03T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID AUTOR = UUID.randomUUID();

    // =================================================================
    // As 9 transições válidas
    // =================================================================

    @Nested
    @DisplayName("transições permitidas")
    class TransicoesPermitidas {

        @Test
        @DisplayName("1) DRAFT → ACTIVE")
        void deve_ativar_rascunho() {
            Contract contrato = umContrato();

            contrato.activate(AGORA);

            assertThat(contrato.status()).isEqualTo(ContractStatus.ACTIVE);
            assertThat(contrato.activatedAt()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("2) DRAFT → CANCELLED")
        void deve_cancelar_rascunho() {
            Contract contrato = umContrato();

            contrato.cancel("Cliente desistiu antes da assinatura", HOJE, AGORA);

            assertThat(contrato.status()).isEqualTo(ContractStatus.CANCELLED);
        }

        @Test
        @DisplayName("3) ACTIVE → SUSPENDED")
        void deve_suspender_ativo() {
            Contract contrato = umContratoAtivo();

            contrato.suspend("Inadimplência acima de 60 dias");

            assertThat(contrato.status()).isEqualTo(ContractStatus.SUSPENDED);
        }

        @Test
        @DisplayName("4) SUSPENDED → ACTIVE")
        void deve_retomar_suspenso() {
            Contract contrato = umContratoAtivo();
            contrato.suspend("Inadimplência acima de 60 dias");

            contrato.resume();

            assertThat(contrato.status()).isEqualTo(ContractStatus.ACTIVE);
        }

        @Test
        @DisplayName("5) ACTIVE → RENEWED, com o sucessor já ativo")
        void deve_renovar_ativo() {
            Contract contrato = umContratoAtivo();

            Contract sucessor = contrato.renew(proximoPeriodo(contrato),
                    Money.of("26400.00", "BRL"), HOJE, AGORA, "chave-1");

            assertThat(contrato.status()).isEqualTo(ContractStatus.RENEWED);
            assertThat(sucessor.status()).isEqualTo(ContractStatus.ACTIVE);
            assertThat(sucessor.previousContractId()).isEqualTo(contrato.id());
        }

        @Test
        @DisplayName("6) ACTIVE → CANCELLED")
        void deve_cancelar_ativo() {
            Contract contrato = umContratoAtivo();

            contrato.cancel("Cliente encerrou a operação na filial", HOJE, AGORA);

            assertThat(contrato.status()).isEqualTo(ContractStatus.CANCELLED);
            assertThat(contrato.cancellationReason())
                    .isEqualTo("Cliente encerrou a operação na filial");
        }

        @Test
        @DisplayName("7) ACTIVE → EXPIRED quando a data de fim já passou")
        void deve_expirar_ativo_vencido() {
            Contract contrato = umContratoAtivo();
            LocalDate depoisDoFim = contrato.period().end().plusDays(1);

            contrato.expire(depoisDoFim);

            assertThat(contrato.status()).isEqualTo(ContractStatus.EXPIRED);
        }

        @Test
        @DisplayName("8) EXPIRED → RENEWED dentro dos 30 dias")
        void deve_renovar_expirado_recente() {
            Contract contrato = umContratoAtivo();
            LocalDate fim = contrato.period().end();
            contrato.expire(fim.plusDays(1));

            LocalDate dia29 = fim.plusDays(29);
            Contract sucessor = contrato.renew(DateRange.of(fim.plusDays(1), fim.plusYears(1)),
                    Money.of("26400.00", "BRL"), dia29, AGORA, null);

            assertThat(contrato.status()).isEqualTo(ContractStatus.RENEWED);
            assertThat(sucessor.status()).isEqualTo(ContractStatus.ACTIVE);
        }

        @Test
        @DisplayName("9) SUSPENDED → CANCELLED")
        void deve_cancelar_suspenso() {
            Contract contrato = umContratoAtivo();
            contrato.suspend("Negociação de dívida em andamento");

            contrato.cancel("Negociação fracassou, encerrar o contrato", HOJE, AGORA);

            assertThat(contrato.status()).isEqualTo(ContractStatus.CANCELLED);
        }
    }

    // =================================================================
    // As transições que a máquina recusa
    // =================================================================

    @Nested
    @DisplayName("transições recusadas")
    class TransicoesRecusadas {

        @Test
        @DisplayName("não ativa um contrato que já está ativo")
        void nao_deve_reativar() {
            Contract contrato = umContratoAtivo();

            assertThatThrownBy(() -> contrato.activate(AGORA))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("não renova contrato cancelado")
        void nao_deve_renovar_cancelado() {
            Contract contrato = umContratoAtivo();
            contrato.cancel("Encerrado por decisão comercial", HOJE, AGORA);

            assertThatThrownBy(() -> contrato.renew(proximoPeriodo(contrato),
                    Money.of("100.00", "BRL"), HOJE, AGORA, null))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("não cancela contrato já renovado — o sucessor é que vale")
        void nao_deve_cancelar_renovado() {
            Contract contrato = umContratoAtivo();
            contrato.renew(proximoPeriodo(contrato), Money.of("100.00", "BRL"),
                    HOJE, AGORA, null);

            assertThatThrownBy(() -> contrato.cancel("Motivo suficientemente longo",
                    HOJE, AGORA))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("não expira contrato ainda vigente, mesmo se a consulta trouxer errado")
        void nao_deve_expirar_vigente() {
            Contract contrato = umContratoAtivo();

            assertThatThrownBy(() -> contrato.expire(HOJE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ainda vigente");
        }

        @Test
        @DisplayName("não expira contrato suspenso — suspensão pede decisão humana")
        void nao_deve_expirar_suspenso() {
            Contract contrato = umContratoAtivo();
            contrato.suspend("Aguardando renegociação com o jurídico");
            LocalDate depois = contrato.period().end().plusDays(1);

            assertThatThrownBy(() -> contrato.expire(depois))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("não edita contrato fora de rascunho")
        void nao_deve_editar_ativo() {
            Contract contrato = umContratoAtivo();

            assertThatThrownBy(() -> contrato.updateDraft("Outro título", null,
                    contrato.period(), Money.of("1.00", "BRL"), BillingCycle.MONTHLY,
                    null, null, null))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("expirado há mais de 30 dias vira contrato novo, não renovação")
        void nao_deve_renovar_expirado_ha_muito_tempo() {
            Contract contrato = umContratoAtivo();
            LocalDate fim = contrato.period().end();
            contrato.expire(fim.plusDays(1));
            LocalDate dia31 = fim.plusDays(31);

            assertThatThrownBy(() -> contrato.renew(
                    DateRange.of(fim.plusDays(1), fim.plusYears(1)),
                    Money.of("100.00", "BRL"), dia31, AGORA, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("mais de 30 dias");
        }

        @Test
        @DisplayName("o novo período não pode começar antes do fim do atual")
        void nao_deve_renovar_com_periodo_sobreposto() {
            Contract contrato = umContratoAtivo();
            DateRange sobreposto = DateRange.of(contrato.period().end().minusDays(10),
                    contrato.period().end().plusYears(1));

            assertThatThrownBy(() -> contrato.renew(sobreposto, Money.of("100.00", "BRL"),
                    HOJE, AGORA, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("não pode começar antes");
        }
    }

    // =================================================================
    // Invariantes de criação
    // =================================================================

    @Nested
    @DisplayName("invariantes")
    class Invariantes {

        @Test
        @DisplayName("valor zero ou negativo é recusado na fábrica")
        void nao_deve_aceitar_valor_nao_positivo() {
            assertThatThrownBy(() -> Contract.create(dados("CT-1", Money.of("0.00", "BRL"))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("maior que zero");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 29, 31})
        @DisplayName("dia de cobrança fora de 1..28 é recusado — 29, 30 e 31 não existem "
                + "em todo mês")
        void nao_deve_aceitar_dia_de_cobranca_invalido(int dia) {
            assertThatThrownBy(() -> Contract.create(new NewContract(CLIENTE, "CT-1", "Título",
                    null, umPeriodo(), Money.of("100.00", "BRL"), BillingCycle.MONTHLY,
                    dia, 0, false, AUTOR)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("entre 1 e 28");
        }

        @Test
        @DisplayName("motivo de cancelamento com menos de 10 caracteres é recusado")
        void nao_deve_aceitar_motivo_curto() {
            Contract contrato = umContratoAtivo();

            assertThatThrownBy(() -> contrato.cancel("ok", HOJE, AGORA))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pelo menos 10");
        }

        @Test
        @DisplayName("data de fim antes do início não chega nem a virar contrato")
        void nao_deve_aceitar_periodo_invertido() {
            assertThatThrownBy(() -> DateRange.of(HOJE, HOJE.minusDays(1)))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("nasce sempre em DRAFT, mesmo que alguém queira outro estado")
        void deve_nascer_em_draft() {
            assertThat(umContrato().status()).isEqualTo(ContractStatus.DRAFT);
        }
    }

    // =================================================================
    // Derivados e numeração
    // =================================================================

    @Nested
    @DisplayName("cálculos derivados")
    class Derivados {

        @Test
        @DisplayName("'vencendo em N dias' é calculado, nunca guardado")
        void deve_calcular_janela_de_aviso() {
            Contract contrato = umContratoAtivo();
            LocalDate trintaDiasAntes = contrato.period().end().minusDays(30);

            assertThat(contrato.isExpiringIn(30, trintaDiasAntes)).isTrue();
            assertThat(contrato.isExpiringIn(15, trintaDiasAntes)).isFalse();
        }

        @Test
        @DisplayName("contrato em rascunho não conta como 'vencendo'")
        void rascunho_nao_esta_vencendo() {
            Contract contrato = umContrato();
            LocalDate trintaDiasAntes = contrato.period().end().minusDays(30);

            assertThat(contrato.isExpiringIn(30, trintaDiasAntes)).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
            "CT-2026-0042, CT-2026-0042-R1",
            "CT-2026-0042-R1, CT-2026-0042-R2",
            "CT-2026-0042-R9, CT-2026-0042-R10"
        })
        @DisplayName("o número do sucessor deriva do anterior e mantém a cadeia legível")
        void deve_derivar_numero_do_sucessor(String atual, String esperado) {
            assertThat(Contract.nextNumber(atual)).isEqualTo(esperado);
        }
    }

    // =================================================================
    // Fixtures
    // =================================================================

    private static Contract umContrato() {
        return Contract.create(dados("CT-2026-0042", Money.of("24000.00", "BRL")));
    }

    private static Contract umContratoAtivo() {
        Contract contrato = umContrato();
        contrato.activate(AGORA);
        return contrato;
    }

    private static NewContract dados(String numero, Money valor) {
        return new NewContract(CLIENTE, numero, "Licença Enterprise", null, umPeriodo(),
                valor, BillingCycle.MONTHLY, 10, 5, true, AUTOR);
    }

    private static DateRange umPeriodo() {
        return DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));
    }

    private static DateRange proximoPeriodo(Contract contrato) {
        LocalDate inicio = contrato.period().end().plusDays(1);
        return DateRange.of(inicio, inicio.plusYears(1));
    }
}
