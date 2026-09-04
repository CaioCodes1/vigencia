package com.caiocodes.crbap.contract.infrastructure;

import com.caiocodes.crbap.client.application.port.ClientContractsPort;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.contract.domain.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responde ao módulo de clientes se aquele cliente tem contrato ativo (RF-03).
 *
 * <p>Substitui o {@code NoContractsYetAdapter} da fase 3, que respondia sempre
 * "não" para a regra já existir enquanto contratos não existiam.
 *
 * <p>Repare na direção: quem <b>declara</b> a porta é o módulo {@code client};
 * quem a implementa é o {@code contract}. Assim {@code client} continua sem
 * importar nada de contratos, e a seta entre os módulos aponta num sentido só —
 * o {@code ArchitectureTest} quebra o build se alguém inverter.
 */
@Component
@RequiredArgsConstructor
public class ClientContractsAdapter implements ClientContractsPort {

    private final ContractRepository contracts;

    @Override
    public boolean hasActiveContracts(ClientId clientId) {
        return contracts.hasActiveContracts(clientId.value());
    }
}
