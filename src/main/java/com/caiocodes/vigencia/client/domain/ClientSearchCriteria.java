package com.caiocodes.vigencia.client.domain;

import java.util.UUID;

/**
 * Filtros da listagem de clientes.
 *
 * @param term             busca parcial por razão social ou nome fantasia,
 *                         sem acento e sem diferenciar maiúsculas
 * @param status           filtro opcional de situação
 * @param accountManagerId <b>não vem da requisição</b>: é o escopo de carteira
 *                         calculado a partir de quem está autenticado. Nulo
 *                         significa "vê todos"
 */
public record ClientSearchCriteria(
        String term,
        ClientStatus status,
        UUID accountManagerId,
        int page,
        int size) {

    private static final int MAX_SIZE = 100;

    public ClientSearchCriteria {
        term = term == null || term.isBlank() ? null : term.strip();
        page = Math.max(page, 0);
        // Teto de propósito: sem ele, um `?size=100000` vira uma varredura da
        // tabela inteira servida como JSON — DoS acidental e gratuito.
        size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
    }
}
