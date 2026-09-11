package com.caiocodes.vigencia.iam.application;

/**
 * Entrada do login. Já chega normalizada: quem traduz o HTTP é o controller.
 */
public record LoginCommand(String email, String password, String userAgent, String ipAddress) {

    public LoginCommand {
        email = email == null ? "" : email.strip().toLowerCase();
    }
}
