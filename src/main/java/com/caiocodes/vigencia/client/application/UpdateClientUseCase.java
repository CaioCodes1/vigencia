package com.caiocodes.vigencia.client.application;

import com.caiocodes.vigencia.audit.application.AuditContext;
import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.client.domain.Client;
import com.caiocodes.vigencia.client.domain.ClientId;
import com.caiocodes.vigencia.client.domain.ClientRepository;
import com.caiocodes.vigencia.shared.application.CurrentUser;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atualiza os dados cadastrais e os contatos.
 *
 * <p>O documento não está aqui de propósito: ele é imutável no agregado. Ver
 * {@link Client}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateClientUseCase {

    private final ClientRepository clients;
    private final CurrentUser currentUser;

    @Transactional
    @Auditable(entity = "Client", action = "UPDATE")
    public ClientDetail execute(ClientCommands.UpdateClient command) {
        Client client = load(command.clientId());
        AuditContext.before(detail(client));
        client.updateProfile(command.legalName(), command.tradeName(), command.email(),
                command.phone(), command.address(), command.notes());
        Client saved = clients.save(client);
        log.info("client.updated clientId={}", saved.id());
        return detail(saved);
    }

    @Transactional
    public ClientDetail addContact(ClientCommands.AddContact command) {
        Client client = load(command.clientId());
        var contact = command.contact();
        client.addContact(contact.name(), contact.email(), contact.phone(), contact.role(),
                contact.primary());
        Client saved = clients.save(client);
        log.info("client.contact_added clientId={}", saved.id());
        return detail(saved);
    }

    private Client load(ClientId id) {
        return clients
                .findByIdInScope(id, currentUser.scopeOrNull(CreateClientUseCase.READ_ALL_PERMISSION))
                .orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    private ClientDetail detail(Client client) {
        return ClientDetail.from(client, currentUser.hasPermission("client:read_sensitive"));
    }
}
