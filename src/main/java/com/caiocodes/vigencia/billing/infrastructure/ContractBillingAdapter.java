package com.caiocodes.vigencia.billing.infrastructure;

import com.caiocodes.vigencia.billing.application.GenerateContractBillingsUseCase;
import com.caiocodes.vigencia.contract.application.port.ContractBillingPort;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responde ao módulo de contratos sobre faturamento.
 *
 * <p>Repare na direção, que é a mesma do {@code ClientContractsAdapter}: quem
 * <b>declara</b> a porta é o módulo que precisa ({@code contract}); quem a
 * implementa é o que sabe fazer ({@code billing}). Assim {@code contract} não
 * importa uma linha de cobranças, mesmo dependendo delas para ativar um
 * contrato.
 */
@Component
@RequiredArgsConstructor
public class ContractBillingAdapter implements ContractBillingPort {

    private final GenerateContractBillingsUseCase generate;

    @Override
    public int generateFor(ActivatedContract contract) {
        return generate.execute(contract);
    }

    @Override
    public int cancelFutureFor(UUID contractId, LocalDate from, String reason) {
        return generate.cancelFuture(contractId, from, reason);
    }
}
