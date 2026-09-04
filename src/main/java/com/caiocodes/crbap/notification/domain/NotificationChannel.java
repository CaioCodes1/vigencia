package com.caiocodes.crbap.notification.domain;

/**
 * Por onde o aviso sai.
 *
 * <p>Existe para que trocar de canal seja configuração, e não um {@code if} no
 * código de negócio: quem manda depende da porta {@code NotificationSender}, e
 * cada canal tem a sua implementação.
 */
public enum NotificationChannel {
    EMAIL, SMS, WEBHOOK, IN_APP
}
