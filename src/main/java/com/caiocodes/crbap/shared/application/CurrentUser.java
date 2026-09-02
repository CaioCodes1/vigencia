package com.caiocodes.crbap.shared.application;

import java.util.UUID;

/**
 * Quem está fazendo a requisição agora.
 *
 * <p>A porta é declarada aqui, no kernel compartilhado, e implementada pelo
 * módulo {@code iam} — que é quem entende de {@code SecurityContext}. Assim o
 * módulo de clientes consegue perguntar "sou vendedor?" sem depender das classes
 * de segurança de outro módulo, e o caso de uso continua testável passando um
 * dublê no construtor.
 */
public interface CurrentUser {

    UUID id();

    boolean hasPermission(String permission);

    /**
     * O id a usar como filtro de carteira, ou {@code null} para "vê tudo".
     *
     * <p>É este método que mata IDOR: o filtro nunca vem de um parâmetro da
     * URL, vem de quem está autenticado. Trocar o id na query string não muda
     * nada.
     *
     * @param globalPermission permissão que dispensa o filtro (ex.:
     *                         {@code dashboard:read_all})
     */
    default UUID scopeOrNull(String globalPermission) {
        return hasPermission(globalPermission) ? null : id();
    }
}
