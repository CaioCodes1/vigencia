package com.caiocodes.vigencia.shared.infrastructure.web;

import com.caiocodes.vigencia.shared.domain.PageResult;
import java.util.List;

/**
 * Envelope único de listagem da API.
 *
 * <p>Um formato só para todas as listagens é o que permite o cliente escrever
 * um componente de paginação e reutilizá-lo em toda tela. E o envelope é
 * <b>nosso</b>, não o {@code Page} do Spring Data: aquele serializa dezenas de
 * campos internos (`pageable`, `sort.unsorted`, `first`, `last`…) que viram
 * contrato público sem ninguém decidir isso.
 */
public record PageResponse<T>(List<T> content, PageInfo page) {

    public record PageInfo(int number, int size, long totalElements, int totalPages) {
    }

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.content(), new PageInfo(
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }
}
