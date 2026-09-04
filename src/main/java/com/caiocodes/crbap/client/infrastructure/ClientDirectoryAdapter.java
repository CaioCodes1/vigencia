package com.caiocodes.crbap.client.infrastructure;

import com.caiocodes.crbap.client.domain.Client;
import com.caiocodes.crbap.client.domain.ClientContact;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.client.domain.ClientRepository;
import com.caiocodes.crbap.shared.application.ClientDirectory;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * O que os outros módulos enxergam de um cliente.
 *
 * <p>A tradução de {@code Client} para {@code ClientRef} acontece aqui, e não no
 * caso de uso que consome, para que nenhum outro módulo segure uma referência ao
 * agregado alheio. Se um dia clientes virarem um serviço separado, troca-se esta
 * classe por uma chamada HTTP e mais nada muda.
 */
@Component
@RequiredArgsConstructor
public class ClientDirectoryAdapter implements ClientDirectory {

    private final ClientRepository clients;

    @Override
    public Optional<ClientRef> findRef(UUID clientId) {
        return clients.findById(ClientId.of(clientId)).map(ClientDirectoryAdapter::toRef);
    }

    private static ClientRef toRef(Client client) {
        return new ClientRef(client.id().value(), client.legalName(), client.isActive(),
                client.accountManagerId(), client.email(),
                client.primaryContact().map(ClientContact::email).orElse(null));
    }
}
