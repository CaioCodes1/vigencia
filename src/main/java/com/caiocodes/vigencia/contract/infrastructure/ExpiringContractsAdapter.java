package com.caiocodes.vigencia.contract.infrastructure;

import com.caiocodes.vigencia.contract.domain.ContractListItem;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.contract.domain.ContractSearchCriteria;
import com.caiocodes.vigencia.contract.domain.ContractStatus;
import com.caiocodes.vigencia.notification.application.port.ExpiringContractsPort;
import com.caiocodes.vigencia.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responde ao módulo de notificações quais contratos vencem numa data.
 *
 * <p>Reusa a busca paginada com {@code endingFrom == endingUntil == data}, que é
 * a forma de dizer "termina exatamente neste dia" — e que cai direto no
 * {@code idx_contracts_expiring}. Uma consulta nova só para isso seria um
 * segundo lugar para manter o mesmo filtro de escopo e status.
 *
 * <p>O escopo de carteira vai <b>nulo</b> de propósito: quem chama é um job, e
 * job não tem carteira. Ele age como o sistema, e precisa enxergar todos os
 * contratos que vencem — senão metade dos clientes não seria avisada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiringContractsAdapter implements ExpiringContractsPort {

    /** O teto que o {@code ContractSearchCriteria} aceita por página. */
    private static final int TAMANHO_PAGINA = 100;

    /**
     * Trava de segurança: 50 páginas, 5.000 contratos vencendo no mesmo dia.
     *
     * <p>Não é o volume esperado — é o limite que impede um laço infinito caso
     * a paginação um dia pare de avançar. Chegar aqui vira aviso no log.
     */
    private static final int MAX_PAGINAS = 50;

    private final ContractRepository contracts;

    @Override
    @Transactional(readOnly = true)
    public List<ContractNotice> findActiveEndingOn(LocalDate endDate) {
        return buscar(ContractStatus.ACTIVE, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractNotice> findExpiredEndedOn(LocalDate endDate) {
        return buscar(ContractStatus.EXPIRED, endDate);
    }

    /**
     * Percorre as páginas até acabar.
     *
     * <p>Pegar só a primeira página seria o bug mais silencioso possível: com
     * 101 contratos vencendo no mesmo dia, um cliente deixa de ser avisado e
     * nada no sistema acusa. Ninguém descobre até ele ligar reclamando.
     */
    private List<ContractNotice> buscar(ContractStatus status, LocalDate endDate) {
        List<ContractNotice> encontrados = new ArrayList<>();
        for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
            PageResult<ContractListItem> resultado = contracts.search(new ContractSearchCriteria(
                    null, status, null, endDate, endDate, null, pagina, TAMANHO_PAGINA));

            resultado.content().stream()
                    .map(ExpiringContractsAdapter::toNotice)
                    .forEach(encontrados::add);

            if (encontrados.size() >= resultado.totalElements()
                    || resultado.content().isEmpty()) {
                return List.copyOf(encontrados);
            }
        }
        log.warn("contract.expiring.teto_de_paginas data={} status={} encontrados={}",
                endDate, status, encontrados.size());
        return List.copyOf(encontrados);
    }

    private static ContractNotice toNotice(ContractListItem item) {
        return new ContractNotice(
                item.id().value(),
                item.clientId(),
                item.number(),
                item.title(),
                item.period().start(),
                item.period().end(),
                item.value().amount(),
                item.value().currency().getCurrencyCode(),
                item.autoRenew());
    }
}
