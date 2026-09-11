package com.caiocodes.vigencia.client.application;

import com.caiocodes.vigencia.client.domain.Client;
import com.caiocodes.vigencia.client.domain.ClientId;
import com.caiocodes.vigencia.client.domain.ClientRepository;
import com.caiocodes.vigencia.client.domain.ClientSearchCriteria;
import com.caiocodes.vigencia.client.domain.ClientStatus;
import com.caiocodes.vigencia.shared.application.CurrentUser;
import com.caiocodes.vigencia.shared.domain.PageResult;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leituras de cliente, sempre dentro do escopo de quem pergunta. */
@Service
@RequiredArgsConstructor
public class GetClientUseCase {

    private final ClientRepository clients;
    private final CurrentUser currentUser;

    /**
     * Devolve <b>404</b>, e não 403, quando o cliente existe mas pertence a
     * outra carteira.
     *
     * <p>Um 403 confirmaria que aquele id existe, e com isso dá para enumerar a
     * base inteira trocando o id na URL. A regra: 404 quando o usuário não
     * deveria nem saber que o recurso existe.
     */
    @Transactional(readOnly = true)
    public ClientDetail byId(ClientId id) {
        Client client = clients
                .findByIdInScope(id, currentUser.scopeOrNull(CreateClientUseCase.READ_ALL_PERMISSION))
                .orElseThrow(() -> new NotFoundException("Cliente", id));
        return ClientDetail.from(client, currentUser.hasPermission("client:read_sensitive"));
    }

    @Transactional(readOnly = true)
    public PageResult<ClientSummary> search(String term, ClientStatus status, int page, int size) {
        // O filtro de carteira NUNCA vem da requisição — vem de quem está
        // autenticado. É o que impede um vendedor de listar a base inteira
        // mandando ?accountManagerId= de outra pessoa.
        ClientSearchCriteria criteria = new ClientSearchCriteria(term, status,
                currentUser.scopeOrNull(CreateClientUseCase.READ_ALL_PERMISSION), page, size);
        return clients.search(criteria).map(ClientSummary::from);
    }
}
