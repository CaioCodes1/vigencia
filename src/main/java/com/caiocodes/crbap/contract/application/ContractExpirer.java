package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractId;
import com.caiocodes.crbap.contract.domain.ContractRepository;
import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expira <b>um</b> contrato, em transação própria.
 *
 * <p><b>Por que é uma classe separada e não um método do
 * {@code ExpireContractsUseCase}:</b> {@code @Transactional} funciona por proxy.
 * Um método chamando outro método da mesma classe não passa pelo proxy, e a
 * anotação é simplesmente ignorada — o {@code REQUIRES_NEW} não aconteceria e o
 * lote inteiro voltaria a compartilhar uma transação só, que é exatamente o
 * problema que a separação existe para resolver. É um bug silencioso: compila,
 * roda, e só aparece quando o primeiro contrato falha e leva os outros junto.
 */
@Component
@RequiredArgsConstructor
public class ContractExpirer {

    private final ContractRepository contracts;
    private final DomainEventRecorder events;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(ContractId id, LocalDate today) {
        // Trava a linha: se alguém estiver renovando este contrato agora, o job
        // espera e depois encontra RENEWED — e o agregado recusa expirar.
        Contract contract = contracts.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Contrato sumiu: " + id));
        contract.expire(today);
        Contract saved = contracts.save(contract);
        events.record(saved);
    }
}
