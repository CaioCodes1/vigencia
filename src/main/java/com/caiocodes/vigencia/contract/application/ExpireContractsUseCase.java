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
 * A varredura diária de vencimentos (RF-11).
 *
 * <p><b>Uma transação por contrato</b>, e não uma para o lote inteiro. Com
 * 5.000 contratos numa transação só, um único erro derrubaria os 4.999 outros —
 * e a transação longa seguraria conexão do pool por minutos. Aqui, um contrato
 * problemático fica para o dia seguinte e os demais expiram normalmente. Quem
 * abre cada transação é o {@link ContractExpirer}, e o Javadoc dele explica por
 * que precisou ser outra classe.
 *
 * <p>Este caso de uso é chamado pelo job agendado, mas não depende dele: o
 * agendador é infraestrutura, a regra mora aqui. Nos testes ela é exercitada
 * direto, com a data controlada pelo {@code Clock}.
 *
 * <p>Note que <b>não há {@code @Transactional} aqui</b>: o método percorre o
 * lote e não escreve nada por conta própria. Anotá-lo criaria a transação
 * longa que a classe existe para evitar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpireContractsUseCase {

    private final ContractRepository contracts;
    private final ContractExpirer expirer;
    private final Clock clock;

    /** @return quantos contratos foram expirados */
    public int execute() {
        LocalDate today = LocalDate.now(clock);
        List<ContractId> vencidos = contracts.findActiveExpiredOn(today);
        if (vencidos.isEmpty()) {
            log.debug("contract.expiry_scan data={} nada a expirar", today);
            return 0;
        }

        int expirados = 0;
        for (ContractId id : vencidos) {
            try {
                expirer.expire(id, today);
                expirados++;
            } catch (DomainException e) {
                // Mudou de estado entre a consulta e o lock (foi renovado, por
                // exemplo). Não é erro: é concorrência normal, e o agregado
                // recusou como devia.
                log.debug("contract.expire.ignorado contractId={} motivo={}", id, e.getMessage());
            } catch (RuntimeException e) {
                log.error("contract.expire.falhou contractId={}", id, e);
            }
        }
        log.info("contract.expiry_scan data={} candidatos={} expirados={}",
                today, vencidos.size(), expirados);
        return expirados;
    }
}
