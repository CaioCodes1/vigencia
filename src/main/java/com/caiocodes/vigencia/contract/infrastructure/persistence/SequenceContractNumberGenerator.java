package com.caiocodes.vigencia.contract.infrastructure.persistence;

import com.caiocodes.vigencia.contract.domain.ContractNumberGenerator;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Número do contrato a partir da sequência do Postgres.
 *
 * <p>{@code nextval} entrega valores distintos mesmo para transações
 * simultâneas, e <b>não volta atrás em rollback</b> — buraco na numeração é
 * aceitável, número repetido não é.
 */
@Component
@RequiredArgsConstructor
public class SequenceContractNumberGenerator implements ContractNumberGenerator {

    private final JdbcTemplate jdbc;

    @Override
    public String next(LocalDate reference) {
        Long sequencial = jdbc.queryForObject("SELECT nextval('contract_number_seq')", Long.class);
        return "CT-%d-%04d".formatted(reference.getYear(),
                sequencial == null ? 1 : sequencial);
    }
}
