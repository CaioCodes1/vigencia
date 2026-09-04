package com.caiocodes.crbap.contract.infrastructure;

import com.caiocodes.crbap.client.domain.Client;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.client.domain.ClientRepository;
import com.caiocodes.crbap.contract.application.port.ContractClientPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * O outro lado da conversa entre os dois módulos: o que o contrato precisa
 * saber sobre o cliente.
 *
 * <p>A tradução de {@code Client} para {@code ClientRef} acontece aqui, e não
 * no caso de uso, para que o módulo de contratos nunca segure uma referência ao
 * agregado de outro módulo. Se um dia clientes virarem um serviço separado,
 * troca-se esta classe por uma chamada HTTP e mais nada muda.
 */
@Component
@RequiredArgsConstructor
public class ClientRefAdapter implements ContractClientPort {

    private final ClientRepository clients;

    @Override
    public Optional<ClientRef> findRef(UUID clientId) {
        return clients.findById(ClientId.of(clientId)).map(ClientRefAdapter::toRef);
    }

    private static ClientRef toRef(Client client) {
        return new ClientRef(client.id().value(), client.legalName(), client.isActive(),
                client.accountManagerId());
    }
}
