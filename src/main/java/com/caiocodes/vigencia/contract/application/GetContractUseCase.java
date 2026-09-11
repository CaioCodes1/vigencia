package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.contract.domain.Contract;
import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.contract.domain.ContractListItem;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.contract.domain.ContractSearchCriteria;
import com.caiocodes.vigencia.contract.domain.ContractStatus;
import com.caiocodes.vigencia.shared.domain.PageResult;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** As leituras de contrato: detalhe, busca, vencimentos e cadeia. */
@Service
@RequiredArgsConstructor
public class GetContractUseCase {

    private static final int MAX_DAYS_AHEAD = 365;
    private static final String DEFAULT_CURRENCY = "BRL";

    private final ContractRepository contracts;
    private final ContractFinder finder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ContractDetail byId(ContractId id) {
        Contract contract = finder.require(id);
        return ContractDetail.from(contract, finder.clientNameOf(contract), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public PageResult<ContractSummary> search(String term, ContractStatus status, UUID clientId,
                                              LocalDate endingUntil, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        PageResult<ContractListItem> result = contracts.search(new ContractSearchCriteria(
                term, status, clientId, null, endingUntil, finder.scope(), page, size));
        return result.map(item -> ContractSummary.from(item, today));
    }

    /**
     * Os contratos que vencem dentro da janela pedida.
     *
     * <p>Só {@code ACTIVE} entra: um contrato suspenso ou já cancelado não
     * "vence" no sentido que interessa a quem olha esta tela — ele já exigiu
     * decisão humana.
     */
    @Transactional(readOnly = true)
    public ExpiringContracts expiring(int daysAhead, UUID clientId) {
        LocalDate today = LocalDate.now(clock);
        int janela = Math.min(Math.max(daysAhead, 1), MAX_DAYS_AHEAD);

        PageResult<ContractListItem> result = contracts.search(new ContractSearchCriteria(
                null, ContractStatus.ACTIVE, clientId, today, today.plusDays(janela),
                finder.scope(), 0, 100));

        List<ContractSummary> content = result.content().stream()
                .map(item -> ContractSummary.from(item, today))
                .toList();

        return new ExpiringContracts(today, janela, resumo(content), content);
    }

    /**
     * A cadeia inteira a partir de qualquer elo — inclusive de um contrato do
     * meio, que é como o usuário costuma chegar nela (abriu o contrato de 2025
     * e quer ver o histórico).
     */
    @Transactional(readOnly = true)
    public ContractChain chain(ContractId id) {
        // Passa pelo finder primeiro: sem isso, bastaria conhecer o id de um
        // contrato de outra carteira para ler a cadeia inteira dele.
        finder.require(id);

        LocalDate today = LocalDate.now(clock);
        List<ContractListItem> elos = contracts.findChain(id);
        if (elos.isEmpty()) {
            throw new NotFoundException("Contrato", id);
        }

        BigDecimal total = elos.stream()
                .map(item -> item.value().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String moeda = elos.get(0).value().currency().getCurrencyCode();

        return new ContractChain(
                elos.stream().map(item -> ContractSummary.from(item, today)).toList(),
                total, moeda, elos.size() - 1);
    }

    private static ExpiringContracts.Summary resumo(List<ContractSummary> content) {
        Map<String, Long> faixas = new LinkedHashMap<>();
        faixas.put("0-7", 0L);
        faixas.put("8-15", 0L);
        faixas.put("16-30", 0L);
        faixas.put("31+", 0L);

        BigDecimal total = BigDecimal.ZERO;
        String moeda = DEFAULT_CURRENCY;
        for (ContractSummary c : content) {
            faixas.merge(faixa(c.daysRemaining()), 1L, Long::sum);
            total = total.add(c.amount());
            moeda = c.currency();
        }
        return new ExpiringContracts.Summary(faixas, content.size(), total, moeda);
    }

    private static String faixa(long dias) {
        if (dias <= 7) {
            return "0-7";
        }
        if (dias <= 15) {
            return "8-15";
        }
        if (dias <= 30) {
            return "16-30";
        }
        return "31+";
    }
}
