package com.caiocodes.vigencia.client.application;

import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.client.domain.Client;
import com.caiocodes.vigencia.client.domain.ClientRepository;
import com.caiocodes.vigencia.shared.application.CurrentUser;
import com.caiocodes.vigencia.shared.domain.Document;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastra um cliente. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateClientUseCase {

    static final String READ_ALL_PERMISSION = "client:read_all";

    private final ClientRepository clients;
    private final CurrentUser currentUser;

    @Transactional
    @Auditable(entity = "Client", action = "CREATE")
    public ClientDetail execute(ClientCommands.CreateClient command) {
        Document document = Document.of(command.document());

        // Checagem amigável, para devolver 409 com mensagem em vez de deixar o
        // banco estourar uma violação de índice. Note que ela NÃO é a garantia:
        // duas requisições simultâneas passam as duas por aqui, e quem impede o
        // duplicado de verdade é o índice único parcial de uk_clients_document_active.
        if (clients.existsActiveWithDocument(document)) {
            throw new BusinessRuleException("DUPLICATE_DOCUMENT",
                    "Já existe um cliente ativo com este documento");
        }

        Client client = Client.create(document, command.legalName(), command.tradeName(),
                command.email(), command.phone(), command.address(),
                resolveAccountManager(command.accountManagerId()));

        if (command.contacts() != null) {
            command.contacts().forEach(c -> client.addContact(
                    c.name(), c.email(), c.phone(), c.role(), c.primary()));
        }

        Client saved = clients.save(client);
        log.info("client.created clientId={} gestor={}", saved.id(), saved.accountManagerId());
        return ClientDetail.from(saved, currentUser.hasPermission("client:read_sensitive"));
    }

    /**
     * Vendedor não escolhe a carteira: o que ele cadastra é dele.
     *
     * <p>Sem esta regra, bastaria mandar o id de outro vendedor no corpo da
     * requisição para plantar um cliente na carteira alheia — e, pior, para
     * tirá-lo da própria visão depois.
     */
    private UUID resolveAccountManager(UUID requested) {
        if (!currentUser.hasPermission(READ_ALL_PERMISSION)) {
            return currentUser.id();
        }
        return requested;
    }
}
