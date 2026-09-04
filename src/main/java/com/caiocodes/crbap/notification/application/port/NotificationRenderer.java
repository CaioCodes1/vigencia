package com.caiocodes.crbap.notification.application.port;

import com.caiocodes.crbap.notification.domain.Notification;

/**
 * Transforma um aviso agendado no texto que vai sair.
 *
 * <p>Porta separada do envio porque são duas responsabilidades com motivos
 * diferentes para mudar: o texto muda quando o comercial reescreve o e-mail; o
 * envio muda quando a empresa troca de provedor.
 */
public interface NotificationRenderer {

    String render(Notification notification);
}
