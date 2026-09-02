package com.caiocodes.crbap.client.infrastructure.persistence;

import com.caiocodes.crbap.client.domain.Client;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.client.domain.ClientListItem;
import com.caiocodes.crbap.client.domain.ClientRepository;
import com.caiocodes.crbap.client.domain.ClientSearchCriteria;
import com.caiocodes.crbap.client.infrastructure.persistence.ClientEntity.ClientStatusValue;
import com.caiocodes.crbap.shared.domain.Document;
import com.caiocodes.crbap.shared.domain.PageResult;
import com.caiocodes.crbap.shared.infrastructure.crypto.BlindIndex;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistência de clientes.
 *
 * <p>É aqui — e só aqui — que o índice cego é calculado. O domínio pede "ache o
 * cliente com este documento"; a tradução para {@code WHERE document_index = ?}
 * é detalhe de infraestrutura.
 */
@Repository
@RequiredArgsConstructor
class ClientRepositoryAdapter implements ClientRepository {

    private final ClientJpaRepository jpa;
    private final ClientPersistenceMapper mapper;
    private final BlindIndex blindIndex;

    @Override
    public Optional<Client> findById(ClientId id) {
        return jpa.findWithContactsById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByIdInScope(ClientId id, UUID accountManagerId) {
        if (accountManagerId == null) {
            return findById(id);
        }
        return jpa.findWithContactsByIdAndAccountManagerId(id.value(), accountManagerId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findActiveByDocument(Document document) {
        return jpa.findWithContactsByDocumentIndexAndStatus(
                        blindIndex.of(document.value()), ClientStatusValue.ACTIVE)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsActiveWithDocument(Document document) {
        return jpa.existsByDocumentIndexAndStatus(
                blindIndex.of(document.value()), ClientStatusValue.ACTIVE);
    }

    @Override
    public PageResult<ClientListItem> search(ClientSearchCriteria criteria) {
        Page<ClientEntity> page = jpa.search(
                criteria.term() == null ? null : "%" + criteria.term() + "%",
                criteria.status() == null ? null : criteria.status().name(),
                criteria.accountManagerId() == null ? null
                        : criteria.accountManagerId().toString(),
                PageRequest.of(criteria.page(), criteria.size()));

        return PageResult.of(page.getContent().stream().map(mapper::toListItem).toList(),
                criteria.page(), criteria.size(), page.getTotalElements());
    }

    /** Devolve o agregado recebido — mesma escolha dos adaptadores do IAM. */
    @Override
    public Client save(Client client) {
        ClientEntity entity = jpa.findWithContactsById(client.id().value())
                .orElseGet(ClientEntity::new);
        mapper.copyToEntity(client, entity, blindIndex.of(client.document().value()));
        jpa.save(entity);
        return client;
    }
}
