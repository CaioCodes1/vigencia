package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.shared.domain.exception.DomainException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Renova sozinho os contratos marcados com {@code autoRenew} que terminam hoje.
 *
 * <p>Roda <b>antes</b> da varredura de expiração, e a ordem é regra de negócio:
 * um contrato com renovação automática nunca deve chegar a {@code EXPIRED}.
 * Invertida, o cliente receberia "seu contrato venceu" e, um minuto depois,
 * "seu contrato foi renovado" — o tipo de sequência que destrói a confiança no
 * sistema inteiro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRenewContractsUseCase {

    private final ContractRepository contracts;
    private final ContractAutoRenewer renewer;
    private final Clock clock;

    /** @return quantos contratos foram renovados */
    public int execute() {
        LocalDate hoje = LocalDate.now(clock);
        List<ContractId> candidatos = contracts.findAutoRenewableEndingOn(hoje);
        if (candidatos.isEmpty()) {
            return 0;
        }

        int renovados = 0;
        for (ContractId id : candidatos) {
            try {
                if (renewer.renew(id, hoje)) {
                    renovados++;
                }
            } catch (DomainException e) {
                // Mudou de estado entre a consulta e o lock (alguém renovou na
                // mão), ou o cliente foi desativado. Não é erro do job.
                log.info("contract.auto_renew.ignorado contractId={} motivo={}",
                        id, e.getMessage());
            } catch (RuntimeException e) {
                log.error("contract.auto_renew.falhou contractId={}", id, e);
            }
        }
        log.info("contract.auto_renew data={} candidatos={} renovados={}",
                hoje, candidatos.size(), renovados);
        return renovados;
    }
}
