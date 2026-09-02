package com.caiocodes.crbap.shared.domain;

import java.util.List;
import java.util.function.Function;

/**
 * Uma página de resultados, sem depender do {@code Page} do Spring Data.
 *
 * <p>O domínio e os casos de uso falam nesta classe; a tradução de e para o
 * {@code Pageable} acontece só no adaptador. Sem isso, a interface de
 * repositório — que vive no domínio — importaria Spring, e o
 * {@code ArchitectureTest} quebraria o build (com razão).
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResult<T> of(List<T> content, int page, int size, long total) {
        return new PageResult<>(List.copyOf(content), page, size, total);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), page, size, 0);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
