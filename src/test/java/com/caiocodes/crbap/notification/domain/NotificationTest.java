package com.caiocodes.crbap.notification.domain;

import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final Instant AGORA = Instant.parse("2026-09-04T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID CONTRATO = UUID.randomUUID();

    @Nested
    @DisplayName("envio")
    class Envio {

        @Test
        @DisplayName("enviado registra o horário e conta a tentativa")
        void deve_marcar_enviado() {
            Notification aviso = umAviso();

            aviso.markSent(AGORA);

            assertThat(aviso.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(aviso.sentAt()).isEqualTo(AGORA);
            assertThat(aviso.attempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("falha transitória mantém PENDING para a próxima rodada")
        void falha_transitoria_deve_manter_pendente() {
            Notification aviso = umAviso();

            boolean desistiu = aviso.markAttemptFailed("SMTP fora do ar", false);

            assertThat(desistiu).isFalse();
            assertThat(aviso.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(aviso.attempts()).isEqualTo(1);
            assertThat(aviso.lastError()).isEqualTo("SMTP fora do ar");
        }

        @Test
        @DisplayName("falha permanente desiste na primeira — retentar não conserta o e-mail")
        void falha_permanente_deve_desistir_na_primeira() {
            Notification aviso = umAviso();

            boolean desistiu = aviso.markAttemptFailed("Destinatário inexistente", true);

            assertThat(desistiu).isTrue();
            assertThat(aviso.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(aviso.attempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("desiste na quarta tentativa transitória")
        void deve_desistir_apos_max_attempts() {
            Notification aviso = umAviso();

            for (int i = 1; i < Notification.MAX_ATTEMPTS; i++) {
                assertThat(aviso.markAttemptFailed("timeout", false)).isFalse();
            }
            boolean desistiu = aviso.markAttemptFailed("timeout", false);

            assertThat(desistiu).isTrue();
            assertThat(aviso.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(aviso.attempts()).isEqualTo(Notification.MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("aviso já enviado não é enviado de novo")
        void nao_deve_enviar_duas_vezes() {
            Notification aviso = umAviso();
            aviso.markSent(AGORA);

            assertThatThrownBy(() -> aviso.markSent(AGORA))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("cancelamento e reenvio")
    class CicloDeVida {

        @Test
        @DisplayName("cancelar impede o envio de um aviso que deixou de fazer sentido")
        void deve_cancelar_pendente() {
            Notification aviso = umAviso();

            aviso.cancel();

            assertThat(aviso.status()).isEqualTo(NotificationStatus.CANCELLED);
            assertThat(aviso.isPending()).isFalse();
        }

        @Test
        @DisplayName("não cancela o que já saiu — o e-mail está na caixa do cliente")
        void nao_deve_cancelar_enviado() {
            Notification aviso = umAviso();
            aviso.markSent(AGORA);

            assertThatThrownBy(aviso::cancel).isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("reenvio devolve para PENDING e zera as tentativas")
        void deve_reenviar_o_que_falhou() {
            Notification aviso = umAviso();
            aviso.markAttemptFailed("Destinatário inexistente", true);

            aviso.retry();

            assertThat(aviso.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(aviso.attempts()).isZero();
            assertThat(aviso.lastError()).isNull();
        }

        @Test
        @DisplayName("não reenvia o que deu certo — seria mandar o mesmo e-mail duas vezes")
        void nao_deve_reenviar_o_que_foi_enviado() {
            Notification aviso = umAviso();
            aviso.markSent(AGORA);

            assertThatThrownBy(aviso::retry)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("falhou");
        }
    }

    @Nested
    @DisplayName("invariantes")
    class Invariantes {

        @Test
        @DisplayName("e-mail inválido é recusado no agendamento, não no envio")
        void deve_recusar_email_invalido() {
            // Descobrir isso na hora do envio custaria uma tentativa, um registro
            // de erro e um lugar a mais para tratar. Aqui custa nada.
            assertThatThrownBy(() -> Notification.schedule(novo("nao-e-email")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("inválido");
        }

        @Test
        @DisplayName("aviso sem contrato e sem cobrança é recusado")
        void deve_exigir_um_alvo() {
            assertThatThrownBy(() -> Notification.schedule(new NewNotification(
                    NotificationType.CONTRACT_EXPIRING, NotificationChannel.EMAIL,
                    CLIENTE, null, null, 30, "cliente@acme.com", "assunto",
                    Map.of(), AGORA)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("contrato ou uma cobrança");
        }

        @Test
        @DisplayName("o destinatário é normalizado para minúsculas")
        void deve_normalizar_o_destinatario() {
            Notification aviso = Notification.schedule(novo("  Cliente@ACME.com  "));

            // O e-mail entra na chave de unicidade. Sem normalizar, "A@x.com" e
            // "a@x.com" seriam dois destinatários e o cliente receberia dois
            // avisos iguais.
            assertThat(aviso.recipient()).isEqualTo("cliente@acme.com");
        }

        @Test
        @DisplayName("nasce PENDING, sem tentativa e sem envio")
        void deve_nascer_pendente() {
            Notification aviso = umAviso();

            assertThat(aviso.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(aviso.attempts()).isZero();
            assertThat(aviso.sentAt()).isNull();
        }
    }

    private static Notification umAviso() {
        return Notification.schedule(novo("cliente@acme.com"));
    }

    private static NewNotification novo(String destinatario) {
        return new NewNotification(
                NotificationType.CONTRACT_EXPIRING,
                NotificationChannel.EMAIL,
                CLIENTE,
                CONTRATO,
                null,
                30,
                destinatario,
                "Contrato CT-2026-0042 vence em 30 dias",
                Map.of("contractNumber", "CT-2026-0042"),
                AGORA);
    }
}
