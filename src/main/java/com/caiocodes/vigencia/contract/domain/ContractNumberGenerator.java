package com.caiocodes.vigencia.contract.domain;

import java.time.LocalDate;

/**
 * Gera o número do contrato quando o usuário não informa um.
 *
 * <p>É uma porta, e não um método estático, porque a unicidade depende de uma
 * sequência do banco: {@code MAX(number) + 1} calculado na aplicação entrega o
 * mesmo valor para duas requisições simultâneas, e a segunda esbarra no
 * {@code uk_contracts_number} com um erro que o usuário não entende.
 */
public interface ContractNumberGenerator {

    /** Formato {@code CT-<ano>-<sequência>}, ex.: {@code CT-2026-0042}. */
    String next(LocalDate reference);
}
