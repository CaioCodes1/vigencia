package com.caiocodes.crbap.contract.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros da busca de contratos.
 *
 * <p>{@code accountManagerId} <b>não</b> é um filtro que o chamador escolhe: ele
 * é preenchido pelo caso de uso a partir de quem está autenticado. Aceitá-lo da
 * query string seria entregar ao vendedor exatamente o parâmetro para ler a
 * carteira alheia.
 *
 * @param term        busca no número e no título
 * @param endingUntil traz o que vence até esta data — é assim que a tela
 *                    "vencendo nos próximos 30 dias" é montada, sem guardar
 *                    nenhum estado "vencendo"
 */
public record ContractSearchCriteria(
        String term,
        ContractStatus status,
        UUID clientId,
        LocalDate endingFrom,
        LocalDate endingUntil,
        UUID accountManagerId,
        int page,
        int size) {

    private static final int MAX_SIZE = 100;

    public ContractSearchCriteria {
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
        term = term == null || term.isBlank() ? null : term.strip();
    }
}
