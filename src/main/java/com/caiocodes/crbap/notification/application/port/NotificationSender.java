package com.caiocodes.crbap.notification.application.port;

import com.caiocodes.crbap.notification.domain.NotificationChannel;

/**
 * Por onde o aviso sai de verdade.
 *
 * <p>Sem Spring, sem SMTP, sem HTTP na assinatura: é o "D" de SOLID valendo
 * dinheiro. Trocar e-mail por webhook do Discord é trocar qual bean implementa
 * esta interface — nenhum {@code if (canal == ...)} aparece no código de
 * negócio, e o caso de uso continua testável com um dublê.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /**
     * Manda, e diz o que aconteceu.
     *
     * <p><b>Não lança</b> em falha esperada: devolve um {@link SendResult} que
     * separa erro transitório de permanente. Lançar obrigaria quem chama a
     * distinguir exceções de bibliotecas diferentes (SMTP, HTTP, SMS) para
     * decidir se vale retentar — e essa é justamente a decisão que cada
     * implementação sabe tomar e o caso de uso não.
     */
    SendResult send(RenderedNotification notification);

    /**
     * @param permanent quando true, retentar não adianta (e-mail inexistente,
     *                  número inválido). Só o transitório vai para nova
     *                  tentativa — retry só para o que pode dar certo depois
     */
    record SendResult(boolean success, boolean permanent, String error) {

        public static SendResult ok() {
            return new SendResult(true, false, null);
        }

        /** Falha que pode dar certo na próxima: SMTP fora do ar, timeout. */
        public static SendResult transientFailure(String error) {
            return new SendResult(false, false, error);
        }

        /** Falha que não muda com o tempo: destinatário inválido, template quebrado. */
        public static SendResult permanentFailure(String error) {
            return new SendResult(false, true, error);
        }
    }

    /** O aviso pronto para sair: assunto e corpo já renderizados. */
    record RenderedNotification(String recipient, String subject, String body) {
    }
}
