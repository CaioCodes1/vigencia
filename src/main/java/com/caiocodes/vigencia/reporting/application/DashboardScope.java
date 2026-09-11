package com.caiocodes.vigencia.reporting.application;

import java.util.UUID;

/**
 * O recorte do painel: a empresa inteira, ou uma carteira só.
 *
 * <p><b>Este record existe por causa do cache.</b> Sem ele, o escopo seria um
 * {@code UUID} solto que alguém esqueceria de colocar na chave — e o vendedor
 * Diego receberia o painel do gestor Bruno, servido quentinho pelo Redis. É o
 * erro de cache mais caro que existe, porque não é uma tela quebrada: é uma
 * tela certa com o dado de outra pessoa, e ninguém percebe.
 *
 * <p>Com o escopo sendo um tipo, a chave de cache nasce dele
 * ({@link #cacheKey()}) e não tem como o filtro entrar na consulta sem entrar
 * na chave.
 */
public record DashboardScope(UUID accountManagerId) {

    /** O painel da empresa inteira — para quem tem {@code dashboard:read_all}. */
    public static DashboardScope all() {
        return new DashboardScope(null);
    }

    public static DashboardScope of(UUID accountManagerId) {
        return new DashboardScope(accountManagerId);
    }

    public boolean global() {
        return accountManagerId == null;
    }

    /** {@code "all"} ou {@code "manager:<uuid>"}. Nunca uma constante fixa. */
    public String cacheKey() {
        return accountManagerId == null ? "all" : "manager:" + accountManagerId;
    }
}
