package com.caiocodes.vigencia.client.domain;

import com.caiocodes.vigencia.shared.domain.Document;
import com.caiocodes.vigencia.shared.domain.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência de clientes.
 *
 * <p>Repare que a busca por documento recebe um {@link Document}, e não o índice
 * cego: o índice é detalhe de infraestrutura (HMAC com chave secreta), e quem o
 * calcula é o adaptador. O domínio pede "ache o cliente com este CPF" e ponto.
 */
public interface ClientRepository {

    Optional<Client> findById(ClientId id);

    /**
     * Busca respeitando a carteira. Com {@code accountManagerId} nulo, enxerga
     * todos — é assim que o escopo do vendedor entra sem um {@code if} no
     * controller.
     */
    Optional<Client> findByIdInScope(ClientId id, UUID accountManagerId);

    Optional<Client> findActiveByDocument(Document document);

    boolean existsActiveWithDocument(Document document);

    /** Devolve a projeção de leitura, não o agregado — ver {@link ClientListItem}. */
    PageResult<ClientListItem> search(ClientSearchCriteria criteria);

    Client save(Client client);
}
